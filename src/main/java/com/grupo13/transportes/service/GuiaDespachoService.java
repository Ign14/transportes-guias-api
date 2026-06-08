package com.grupo13.transportes.service;

import com.grupo13.transportes.dto.ActualizarGuiaRequest;
import com.grupo13.transportes.dto.CrearGuiaRequest;
import com.grupo13.transportes.entity.EstadoGuia;
import com.grupo13.transportes.entity.GuiaDespacho;
import com.grupo13.transportes.exception.GuiaNoEncontradaException;
import com.grupo13.transportes.repository.GuiaDespachoRepository;
import com.grupo13.transportes.security.ValidadorPermisos;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Orquesta el ciclo de vida completo de una guia:
 * generacion del PDF -> EFS (temporal) -> S3 (final) -> descarga -> actualizacion -> eliminacion.
 */
@Slf4j
@Service
public class GuiaDespachoService {

    private final GuiaDespachoRepository repository;
    private final PdfGuiaService pdfService;
    private final EfsStorageService efsService;
    private final S3StorageService s3Service;
    private final ValidadorPermisos validadorPermisos;

    public GuiaDespachoService(GuiaDespachoRepository repository,
                               PdfGuiaService pdfService,
                               EfsStorageService efsService,
                               S3StorageService s3Service,
                               ValidadorPermisos validadorPermisos) {
        this.repository = repository;
        this.pdfService = pdfService;
        this.efsService = efsService;
        this.s3Service = s3Service;
        this.validadorPermisos = validadorPermisos;
    }

    /**
     * 1. Crea la guia, genera el PDF y lo guarda TEMPORALMENTE en EFS.
     * Estado resultante: GENERADA.
     */
    @Transactional
    public GuiaDespacho crearGuia(CrearGuiaRequest req, String creadoPor) {
        LocalDate hoy = LocalDate.now();

        GuiaDespacho guia = GuiaDespacho.builder()
                .numeroGuia(generarNumeroGuia())
                .transportista(req.transportista())
                .rutTransportista(req.rutTransportista())
                .cliente(req.cliente())
                .direccionOrigen(req.direccionOrigen())
                .direccionDestino(req.direccionDestino())
                .fecha(hoy)
                .estado(EstadoGuia.GENERADA)
                .creadoPor(StringUtils.hasText(creadoPor) ? creadoPor : "sistema")
                .build();

        // Persistimos primero para obtener id y numeroGuia definitivos.
        guia = repository.save(guia);

        // Construir la ruta relativa (misma estructura en EFS y S3).
        String rutaRelativa = construirRutaRelativa(guia);

        // Generar PDF y guardar en EFS (paso obligatorio antes de S3).
        byte[] pdf = pdfService.generarPdf(guia);
        String efsPath = efsService.guardar(rutaRelativa, pdf);

        guia.setEfsPath(efsPath);
        guia.setS3Key(rutaRelativa); // key prevista en S3 (aun no subida)
        return repository.save(guia);
    }

    /**
     * 2. Sube la guia desde EFS hacia S3. Estado resultante: SUBIDA_S3.
     */
    @Transactional
    public GuiaDespacho subirAS3(Long id) {
        GuiaDespacho guia = obtenerEntidad(id);
        if (!StringUtils.hasText(guia.getEfsPath())) {
            // Regenera en EFS si por algun motivo no existe.
            String rutaRelativa = construirRutaRelativa(guia);
            guia.setEfsPath(efsService.guardar(rutaRelativa, pdfService.generarPdf(guia)));
            guia.setS3Key(rutaRelativa);
        }
        s3Service.subirDesdeArchivo(guia.getS3Key(), guia.getEfsPath());
        guia.setEstado(EstadoGuia.SUBIDA_S3);
        return repository.save(guia);
    }

    /**
     * 3. Descarga la guia desde S3 validando permisos (ADMIN / TRANSPORTISTA).
     */
    public byte[] descargar(Long id, String rolHeader, String usuarioHeader) {
        GuiaDespacho guia = obtenerEntidad(id);
        validadorPermisos.validarDescarga(guia, rolHeader, usuarioHeader);
        return s3Service.descargar(guia.getS3Key());
    }

    /**
     * 4. Modifica la guia, regenera el PDF y lo reemplaza en EFS y S3.
     * Estado resultante: ACTUALIZADA.
     */
    @Transactional
    public GuiaDespacho actualizarGuia(Long id, ActualizarGuiaRequest req) {
        GuiaDespacho guia = obtenerEntidad(id);

        if (StringUtils.hasText(req.transportista())) guia.setTransportista(req.transportista());
        if (req.rutTransportista() != null) guia.setRutTransportista(req.rutTransportista());
        if (req.cliente() != null) guia.setCliente(req.cliente());
        if (req.direccionOrigen() != null) guia.setDireccionOrigen(req.direccionOrigen());
        if (req.direccionDestino() != null) guia.setDireccionDestino(req.direccionDestino());

        guia.setEstado(EstadoGuia.ACTUALIZADA);

        // Regenerar PDF con los nuevos datos en EFS.
        String rutaRelativa = construirRutaRelativa(guia);
        byte[] pdf = pdfService.generarPdf(guia);
        String efsPath = efsService.guardar(rutaRelativa, pdf);
        guia.setEfsPath(efsPath);
        guia.setS3Key(rutaRelativa);

        // Reemplazar el objeto en S3.
        s3Service.subirDesdeArchivo(guia.getS3Key(), guia.getEfsPath());

        return repository.save(guia);
    }

    /**
     * 5. Elimina la guia de S3 y actualiza su estado a ELIMINADA.
     * El registro permanece en el historial.
     */
    @Transactional
    public GuiaDespacho eliminarGuia(Long id) {
        GuiaDespacho guia = obtenerEntidad(id);
        if (StringUtils.hasText(guia.getS3Key())) {
            s3Service.eliminar(guia.getS3Key());
        }
        efsService.eliminar(guia.getEfsPath());
        guia.setEstado(EstadoGuia.ELIMINADA);
        return repository.save(guia);
    }

    /**
     * 6. Historial filtrando por transportista y/o fecha (ambos opcionales).
     */
    public List<GuiaDespacho> consultarHistorial(String transportista, LocalDate fecha) {
        String t = StringUtils.hasText(transportista) ? transportista : null;
        return repository.buscarHistorial(t, fecha);
    }

    /** 7. Detalle de una guia. */
    public GuiaDespacho obtenerEntidad(Long id) {
        return repository.findById(id).orElseThrow(() -> new GuiaNoEncontradaException(id));
    }

    // ---------- utilidades ----------

    private String generarNumeroGuia() {
        long secuencia = repository.count() + 1;
        return String.format("GD-%d-%06d", LocalDate.now().getYear(), secuencia);
    }

    /**
     * Estructura: guias/<yyyy-MM-dd>/<transportista-normalizado>/<numeroGuia>.pdf
     * Ej: guias/2026-06-08/transportista-x/GD-2026-000123.pdf
     */
    private String construirRutaRelativa(GuiaDespacho guia) {
        String fecha = guia.getFecha().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String transp = normalizar(guia.getTransportista());
        return String.format("guias/%s/%s/%s.pdf", fecha, transp, guia.getNumeroGuia());
    }

    private String normalizar(String s) {
        if (s == null) return "sin-transportista";
        return s.trim().toLowerCase()
                .replaceAll("\\s+", "-")
                .replaceAll("[^a-z0-9\\-]", "");
    }
}
