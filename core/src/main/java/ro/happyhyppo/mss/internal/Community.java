package ro.happyhyppo.mss.internal;

public class Community {

    private String readOnly;

    private String readWrite;

    public Community() {
    }

    public Community(String readOnly, String readWrite) {
        this.readOnly = readOnly;
        this.readWrite = readWrite;
    }

    public String getReadOnly() {
        return readOnly;
    }

    public void setReadOnly(String readOnly) {
        this.readOnly = readOnly;
    }

    public String getReadWrite() {
        return readWrite;
    }

    public void setReadWrite(String readWrite) {
        this.readWrite = readWrite;
    }

    public boolean canRead(String comm) {
        return readOnly.equals(comm) || readWrite.equals(comm);
    }

    public boolean canWrite(String comm) {
        return readWrite.equals(comm);
    }

}
