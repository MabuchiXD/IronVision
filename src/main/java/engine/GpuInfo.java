package engine;

public class GpuInfo {
    private String name = "N/A";
    private long vramTotal = 0;
    private long vramUsed = 0;
    private double gpuLoad = 0.0;
    private double temperature = 0.0;

    public GpuInfo() {}

    public String getName() { return name; }
    public long getVramTotal() { return vramTotal; }
    public long getVramUsed() { return vramUsed; }
    public double getGpuLoad() { return gpuLoad; }
    public double getTemperature() { return temperature; }

    public void setName(String name) {
        this.name = name != null ? name : "N/A";
    }

    public void setVramTotal(long vramTotal) {
        this.vramTotal = Math.max(0, vramTotal);
    }

    public void setVramUsed(long vramUsed) {
        this.vramUsed = Math.max(0, vramUsed);
        if (vramTotal > 0 && this.vramUsed > vramTotal) {
            this.vramUsed = vramTotal;
        }
    }

    public void setGpuLoad(double gpuLoad) {
        this.gpuLoad = Math.max(0, Math.min(100, gpuLoad));
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public String getFormattedVramInfo() {
        if (vramTotal == 0) return "VRAM: нет данных";
        long totalMB = vramTotal / 1024 / 1024;
        long usedMB = vramUsed / 1024 / 1024;
        double percent = (double) usedMB / totalMB * 100;
        return String.format("VRAM: %,d / %,d MB (%.1f%%)", usedMB, totalMB, percent);
    }

    public String getFormattedTemperature() {
        if (temperature <= 0) return "";
        return String.format(" | %.0f°C", temperature);
    }
}