# Checklist de evidencias y guion del video

Documento para preparar las **evidencias** (Postman, EC2, S3, Docker, GitHub Actions) y grabar el
**video explicativo** (20 pts). Cada bloque indica qué mostrar y con qué criterio de la pauta se
relaciona.

---

## A. Tabla de pruebas (Postman)

| # | Acción                | Método / Endpoint                                   | Cabeceras                                   | Resultado esperado                                  |
|---|-----------------------|-----------------------------------------------------|---------------------------------------------|-----------------------------------------------------|
| 1 | Crear guía            | `POST /api/guias`                                   | `X-Usuario-Nombre`                          | `201`, estado `GENERADA`, `efsPath` poblado         |
| 2 | Subir a S3            | `POST /api/guias/{id}/subir-s3`                     | —                                           | `200`, estado `SUBIDA_S3`, `s3Key` poblado          |
| 3 | Descargar (ADMIN)     | `GET /api/guias/{id}/descargar`                     | `X-Usuario-Rol: ADMIN`                      | `200`, devuelve el PDF                               |
| 3b| Descargar (propio)    | `GET /api/guias/{id}/descargar`                     | `X-Usuario-Rol: TRANSPORTISTA` + nombre OK  | `200`, devuelve el PDF                               |
| 3c| Descargar (ajeno)     | `GET /api/guias/{id}/descargar`                     | `X-Usuario-Rol: TRANSPORTISTA` + nombre ≠   | `403 Forbidden`                                     |
| 4 | Actualizar            | `PUT /api/guias/{id}`                               | —                                           | `200`, estado `ACTUALIZADA`, PDF reemplazado en S3  |
| 5 | Historial             | `GET /api/guias?transportista=&fecha=`             | —                                           | `200`, lista filtrada                               |
| 6 | Detalle               | `GET /api/guias/{id}`                               | —                                           | `200`, datos de la guía                             |
| 7 | Eliminar              | `DELETE /api/guias/{id}`                            | —                                           | `200`, estado `ELIMINADA`, objeto borrado de S3     |
| 8 | Health                | `GET /actuator/health`                              | —                                           | `200`, `{"status":"UP"}`                            |

## B. Capturas / evidencias a recopilar

- [ ] **Postman**: ejecución de cada request de la tabla anterior con su respuesta.
- [ ] **EC2 (consola SSH)**: `df -h | grep efs` mostrando EFS montado en `/mnt/efs`.
- [ ] **EC2**: `sudo find /mnt/efs/guias -type f` mostrando el PDF generado en EFS.
- [ ] **EC2**: `docker ps` con el contenedor `transportes-guias-api` corriendo.
- [ ] **EC2**: `docker inspect` o el comando `docker run` evidenciando `-v /mnt/efs:/app/efs`.
- [ ] **S3 (consola AWS)**: bucket privado con la estructura `guias/<fecha>/<transportista>/<archivo>.pdf`.
- [ ] **S3**: el mismo objeto tras un `PUT` (fecha de modificación actualizada) y su desaparición tras `DELETE`.
- [ ] **Docker Hub**: imagen `TU_USUARIO/transportes-guias-api` publicada con tags `latest` y `<sha>`.
- [ ] **GitHub Actions**: workflow en verde con los jobs `build-and-push` y `deploy-ec2`.

## C. Guion sugerido del video (≈ 8–12 min)

1. **Introducción (30 s)** — Presentar el caso (empresa transportista, guías de despacho) y el
   Grupo 13. Mostrar el diagrama de arquitectura del README.

2. **Recorrido del código (2 min)** — Mostrar la estructura por capas: `controller`, `service`
   (Pdf, Efs, S3, orquestador), `repository`, `entity`, `dto`, `config`, `exception`, `security`.
   Enfatizar que **no hay credenciales hardcodeadas** (todo por variables de entorno) — *abrir
   `application.yml` y `S3Config`*.

3. **EFS — 15 pts** — En la EC2: mostrar `df -h | grep efs` (EFS en `/mnt/efs`). Crear una guía
   con Postman y luego `sudo find /mnt/efs/guias` para evidenciar que el PDF se generó **primero
   en EFS**. Explicar `EfsStorageService`.

4. **Subida a S3 — 10 pts** — Ejecutar `POST /{id}/subir-s3`. En la consola de S3 mostrar el
   objeto en `guias/<fecha>/<transportista>/`. Resaltar que el bucket es **privado**.

5. **Modificación/actualización en S3 — 15 pts** — Ejecutar `PUT /{id}` cambiando datos.
   Mostrar en S3 que el objeto fue **reemplazado** (cambia la fecha de modificación) y que el
   estado de la guía pasa a `ACTUALIZADA`.

6. **Descarga con permisos — 10 pts** — Mostrar descarga `ADMIN` (200), descarga del propio
   transportista (200) y descarga de un transportista ajeno (**403**). Explicar `ValidadorPermisos`.

7. **Historial — 10 pts** — Ejecutar `GET /api/guias?transportista=&fecha=` y mostrar el filtrado.

8. **Pipeline automatizado — 20 pts** — Hacer un `git push` a `main` en vivo (o mostrar una
   ejecución previa). Mostrar GitHub Actions en verde, la imagen publicada en Docker Hub y el
   contenedor desplegado en EC2 con `docker ps` y el volumen EFS montado.

9. **Cierre (30 s)** — Relacionar cada función con la pauta y mencionar las consideraciones de
   AWS Academy (credenciales temporales con `AWS_SESSION_TOKEN`).

## D. Antes de grabar — verificación rápida

- [ ] `mvn clean package` compila y los tests pasan.
- [ ] GitHub Secrets configurados (Docker Hub, EC2, AWS) y **credenciales del Learner Lab vigentes**.
- [ ] Bucket S3 creado y privado; EFS montado en `/mnt/efs`; Security Groups con puertos 22, 8080 y NFS 2049.
- [ ] Colección Postman importada y `baseUrl` apuntando a `http://TU_IP_EC2:8080`.
