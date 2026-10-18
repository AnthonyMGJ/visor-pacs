package pe.upn.visorpacs.recuperacion;

/** Envío de correos de texto plano. La implementación de producción no bloquea al que llama. */
public interface EnviadorCorreo {
    void enviar(String para, String asunto, String texto);
}
