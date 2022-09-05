package ro.happyhyppo.mss.web.model;

public class TrapManagerDTO {

    private String ipAddress;

    private Integer port;

    public TrapManagerDTO() {
    }

    public TrapManagerDTO(String ipAddress, Integer port) {
        this.ipAddress = ipAddress;
        this.port = port;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public Integer getPort() {
        return port;
    }

}
