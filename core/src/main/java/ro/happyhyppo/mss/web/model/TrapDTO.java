package ro.happyhyppo.mss.web.model;

import java.util.List;

public class TrapDTO {

    private String oid;

    private List<TrapObjectDTO> objects;

    public String getOid() {
        return oid;
    }

    public List<TrapObjectDTO> getObjects() {
        return objects;
    }

}
