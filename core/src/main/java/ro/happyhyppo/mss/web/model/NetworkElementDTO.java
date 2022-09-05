package ro.happyhyppo.mss.web.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import ro.happyhyppo.mss.net.NetworkElement;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NetworkElementDTO {

	private String ipAddress;

	private Integer port;

	private String readCommunity;

	private String writeCommunity;

	@JsonProperty("class")
	private String neClass;

	private Integer http;

	private TrapManagerDTO trapManager;

	private Map<String, String> values;

    public NetworkElementDTO() {
	}

	public NetworkElementDTO(NetworkElement networkElement) {
		ipAddress = networkElement.getIpAddress();
		port = networkElement.getPort();
		readCommunity = networkElement.getCommunity().getReadOnly();
		writeCommunity = networkElement.getCommunity().getReadWrite();
		neClass = networkElement.getClass().getName();
		http = networkElement.getHttp();
		trapManager = new TrapManagerDTO(networkElement.getTrapManager().getIpddress(), networkElement.getTrapManager().getPort());
	}

	public String getIpAddress() {
		return ipAddress;
	}

	public String getNeClass() {
		return neClass;
	}

	public Integer getPort() {
		return port != null ? port : 161;
	}

	public String getReadCommunity() {
		return readCommunity != null ? readCommunity : "public";
	}

	public String getWriteCommunity() {
		return writeCommunity != null ? writeCommunity : "public";
	}

	public Integer getHttp() {
		return http != null ? http : 0;
	}

    public TrapManagerDTO getTrapManager() {
        return trapManager;
    }

    @JsonIgnore
    public String getTrapManagerIpAddress() {
        return trapManager != null? (trapManager.getIpAddress() != null ? trapManager.getIpAddress(): "127.0.0.1") : "127.0.0.1";
    }

    @JsonIgnore
    public Integer getTrapManagerPort() {
        return trapManager != null? (trapManager.getPort() != null ? trapManager.getPort(): 162) : 162;
    }

    @JsonInclude(Include.NON_NULL)
    public Map<String, String> getValues() {
        return values;
    }

    public void setValues(Map<String, String> values) {
        this.values = values;
    }

}
