package com.grupo13.transportes.controller;

import com.grupo13.transportes.dto.ActualizarGuiaRequest;
import com.grupo13.transportes.dto.CrearGuiaRequest;
import com.grupo13.transportes.dto.GuiaResponse;
import com.grupo13.transportes.entity.GuiaDespacho;
import com.grupo13.transportes.service.GuiaDespachoService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * API REST de guias de despacho.
 *
 * Permisos de descarga: cabeceras X-Usuario-Rol (ADMIN|TRANSPORTISTA)
 * y X-Usuario-Nombre (nombre del transportista cuando aplica).
 */
@RestController
@RequestMapping("/api/guias")
public class GuiaDespachoController {

    private final GuiaDespachoService service;

    public GuiaDespachoController(GuiaDespachoService service) {
        this.service = service;
    }

    /** POST /api/guias -> crea la guia, genera el PDF y lo guarda en EFS. */
    @PostMapping
    public ResponseEntity<GuiaResponse> crear(
            @Valid @RequestBody CrearGuiaRequest req,
            @RequestHeader(value = "X-Usuario-Nombre", required = false) String usuario) {
        GuiaDespacho guia = service.crearGuia(req, usuario);
        return ResponseEntity.status(201).body(GuiaResponse.from(guia));
    }

    /** POST /api/guias/{id}/subir-s3 -> sube la guia desde EFS hacia S3. */
    @PostMapping("/{id}/subir-s3")
    public ResponseEntity<GuiaResponse> subirS3(@PathVariable Long id) {
        return ResponseEntity.ok(GuiaResponse.from(service.subirAS3(id)));
    }

    /** GET /api/guias/{id}/descargar -> descarga desde S3 validando permisos. */
    @GetMapping("/{id}/descargar")
    public ResponseEntity<byte[]> descargar(
            @PathVariable Long id,
            @RequestHeader(value = "X-Usuario-Rol", required = false) String rol,
            @RequestHeader(value = "X-Usuario-Nombre", required = false) String usuario) {
        GuiaDespacho guia = service.obtenerEntidad(id);
        byte[] pdf = service.descargar(id, rol, usuario);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + guia.getNumeroGuia() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /** PUT /api/guias/{id} -> modifica, regenera el PDF y reemplaza en S3. */
    @PutMapping("/{id}")
    public ResponseEntity<GuiaResponse> actualizar(
            @PathVariable Long id,
            @RequestBody ActualizarGuiaRequest req) {
        return ResponseEntity.ok(GuiaResponse.from(service.actualizarGuia(id, req)));
    }

    /** DELETE /api/guias/{id} -> elimina de S3 y marca estado ELIMINADA. */
    @DeleteMapping("/{id}")
    public ResponseEntity<GuiaResponse> eliminar(@PathVariable Long id) {
        return ResponseEntity.ok(GuiaResponse.from(service.eliminarGuia(id)));
    }

    /** GET /api/guias?transportista=&fecha= -> historial filtrado. */
    @GetMapping
    public ResponseEntity<List<GuiaResponse>> historial(
            @RequestParam(required = false) String transportista,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        List<GuiaResponse> lista = service.consultarHistorial(transportista, fecha)
                .stream().map(GuiaResponse::from).toList();
        return ResponseEntity.ok(lista);
    }

    /** GET /api/guias/{id} -> detalle de una guia. */
    @GetMapping("/{id}")
    public ResponseEntity<GuiaResponse> detalle(@PathVariable Long id) {
        return ResponseEntity.ok(GuiaResponse.from(service.obtenerEntidad(id)));
    }
}
