package pe.upn.visorpacs.auth;

public class AccountLockedException extends RuntimeException {
    public AccountLockedException() {
        super("Cuenta bloqueada temporalmente");
    }
}
