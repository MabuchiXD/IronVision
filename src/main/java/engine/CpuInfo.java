package engine;

public class CpuInfo {
    private double usage = 0.0;
    private double baseClock = 0.0;
    private String name = "Не определено";
    private int cores = 0;
    private double temperature = 0.0;

    public CpuInfo() {}

    public double getUsage() { return usage; }
    public double getBaseClock() { return baseClock; }
    public String getName() { return name; }
    public int getCores() { return cores; }
    public double getTemperature() { return temperature; }

    public void setUsage(double usage) {
        this.usage = Math.max(0, Math.min(100, usage));
    }

    public void setBaseClock(double baseClock) {
        this.baseClock = Math.max(0, baseClock);
    }

    public void setName(String name) {
        this.name = name != null && !name.trim().isEmpty() ? name.trim() : "Не определено";
    }

    public void setCores(int cores) {
        this.cores = Math.max(0, cores);
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public String getFormattedUsage() {
        return String.format("%.1f%%", usage);
    }

    public String getFormattedClock() {
        if (baseClock >= 1000) {
            return String.format("%.2f GHz", baseClock / 1000.0);
        } else {
            return String.format("%.0f MHz", baseClock);
        }
    }

    public String getFormattedTemperature() {
        if (temperature <= 0) return "N/A";
        return String.format("%.0f°C", temperature);
    }
}