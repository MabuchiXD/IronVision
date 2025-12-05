package engine;

public class RamInfo {
    private long total = 0;
    private long free = 0;
    private long used = 0;

    public RamInfo() {}

    public void setTotal(long total) {
        this.total = Math.max(0, total);
        calculateUsed();
    }

    public void setFree(long free) {
        this.free = Math.max(0, Math.min(total, free));
        this.used = total - this.free;
    }

    public void setUsed(long used) {
        this.used = Math.max(0, Math.min(total, used));
        this.free = total - this.used;
    }

    private void calculateUsed() {
        this.used = total - free;
    }

    public double getUsagePercent() {
        if (total == 0) return 0.0;
        return (double) used / total * 100.0;
    }

    public String getFormattedTotal() {
        return formatBytes(total);
    }

    public String getFormattedFree() {
        return formatBytes(free);
    }

    public String getFormattedUsed() {
        return formatBytes(used);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes / Math.pow(1024, exp);
        return String.format("%.1f %s", value, units[exp-1]);
    }
}