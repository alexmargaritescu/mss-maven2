package ro.happyhyppo.mss.internal;

public class Authority {

    private String securityName;
    private String securityLevel;
    private String authPassphrase;
    private String privPassphrase;
    private boolean invalid;

    public Authority() {
    }

    public Authority(String securityName, String securityLevel, String authPassphrase, String privPassphrase) {
        this.securityName = securityName;
        this.securityLevel = securityLevel;
        this.authPassphrase = authPassphrase;
        this.privPassphrase = privPassphrase;
    }

    public String getSecurityName() {
        return securityName;
    }

    public String getSecurityLevel() {
        return securityLevel;
    }

    public String getAuthPassphrase() {
        return authPassphrase;
    }

    public String getPrivPassphrase() {
        return privPassphrase;
    }

    public boolean isValid() {
        return !invalid;
    }

    public void setInvalid() {
        this.invalid = true;
    }

}
