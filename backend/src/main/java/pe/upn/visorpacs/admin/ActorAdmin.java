package pe.upn.visorpacs.admin;

/** Administrador que ejecuta una operación (sale del token). Se usa para auditar y para evitar auto-bloqueos. */
public record ActorAdmin(int id, String username) {}
