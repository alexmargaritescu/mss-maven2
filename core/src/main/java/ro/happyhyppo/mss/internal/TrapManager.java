package ro.happyhyppo.mss.internal;

public class TrapManager {

    private String ipddress;

    private int port;

    private String bootTrap;

    public TrapManager(String ipddress, int port, String bootTrap) {
        this.ipddress = ipddress;
        this.port = port;
        this.bootTrap = bootTrap;
    }

    public String getIpddress() {
        return ipddress;
    }

    public int getPort() {
        return port;
    }

    String getBootTrap() {
        return bootTrap;
    }

    @Override
    public String toString() {
        return ipddress + ":" + port;
    }

}
