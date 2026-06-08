package com.grupo13.transportes.security;

import com.grupo13.transportes.entity.GuiaDespacho;
import com.grupo13.transportes.exception.AccesoDenegadoException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Validacion de permisos simple basada en rol enviado por cabeceras HTTP
 * (X-Usuario-Rol y X-Usuario-Nombre).
 *
 * Regla de negocio para la DESCARGA de guias:
 *  - ADMIN: puede descargar cualquier guia.
 *  - TRANSPORTISTA: solo puede descargar guias cuyo campo "transportista"
 *    coincide con su propio nombre de usuario.
 *
 * En un entorno productivo esto se reemplazaria por Spring Security + JWT;
 * para la actividad academica se mantiene explicito y facil de demostrar.
 */
@Component
public class ValidadorPermisos {

    public Rol parseRol(String rolHeader) {
        if (!StringUtils.hasText(rolHeader)) {
            throw new AccesoDenegadoException("Falta la cabecera X-Usuario-Rol (ADMIN o TRANSPORTISTA)");
        }
        try {
            return Rol.valueOf(rolHeader.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AccesoDenegadoException("Rol invalido: " + rolHeader + ". Use ADMIN o TRANSPORTISTA");
        }
    }

    /**
     * Valida que el usuario pueda descargar la guia indicada.
     */
    public void validarDescarga(GuiaDespacho guia, String rolHeader, String usuarioHeader) {
        Rol rol = parseRol(rolHeader);

        if (rol == Rol.ADMIN) {
            return; // ADMIN tiene acceso total
        }

        // TRANSPORTISTA: debe identificarse y coincidir con el dueno de la guia.
        if (!StringUtils.hasText(usuarioHeader)) {
            throw new AccesoDenegadoException(
                    "El rol TRANSPORTISTA debe enviar la cabecera X-Usuario-Nombre");
        }
        if (!usuarioHeader.trim().equalsIgnoreCase(guia.getTransportista())) {
            throw new AccesoDenegadoException(
                    "El transportista '" + usuarioHeader + "' no tiene permiso para descargar guias de '"
                            + guia.getTransportista() + "'");
        }
    }
}
