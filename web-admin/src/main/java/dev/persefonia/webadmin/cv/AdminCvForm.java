package dev.persefonia.webadmin.cv;

public final class AdminCvForm {
    private String trAssetId = "";
    private String trDisplayLabel = "";
    private String enAssetId = "";
    private String enDisplayLabel = "";

    public String getTrAssetId() {
        return trAssetId;
    }

    public void setTrAssetId(String trAssetId) {
        this.trAssetId = trAssetId == null ? "" : trAssetId;
    }

    public String getTrDisplayLabel() {
        return trDisplayLabel;
    }

    public void setTrDisplayLabel(String trDisplayLabel) {
        this.trDisplayLabel = trDisplayLabel == null ? "" : trDisplayLabel;
    }

    public String getEnAssetId() {
        return enAssetId;
    }

    public void setEnAssetId(String enAssetId) {
        this.enAssetId = enAssetId == null ? "" : enAssetId;
    }

    public String getEnDisplayLabel() {
        return enDisplayLabel;
    }

    public void setEnDisplayLabel(String enDisplayLabel) {
        this.enDisplayLabel = enDisplayLabel == null ? "" : enDisplayLabel;
    }
}
