package engine;

public class DiskInfo {
    private String name;
    private long totalSpace;
    private long freeSpace;
    private long usedSpace;
    private double usagePercent;

    public DiskInfo() {
        this.name = "Не определен";
        this.totalSpace = 0;
        this.freeSpace = 0;
        this.usedSpace = 0;
        this.usagePercent = 0.0;
    }

    public String getName() { return name; }
    public long getTotalSpace() { return totalSpace; }
    public long getFreeSpace() { return freeSpace; }
    public long getUsedSpace() { return usedSpace; }
    public double getUsagePercent() { return usagePercent; }

    public void setName(String name) {
        this.name = name != null ? name : "Не определен";
    }

    public void setTotalSpace(long totalSpace) {
        this.totalSpace = Math.max(0, totalSpace);
        calculateUsage();
    }

    public void setFreeSpace(long freeSpace) {
        this.freeSpace = Math.max(0, Math.min(totalSpace, freeSpace));
        this.usedSpace = totalSpace - this.freeSpace;
        calculateUsage();
    }

    private void calculateUsage() {
        if (totalSpace > 0) {
            usagePercent = (double) usedSpace / totalSpace * 100.0;
            usagePercent = Math.max(0, Math.min(100, usagePercent));
        }
    }

    public String getFormattedTotalSpace() {
        return formatBytes(totalSpace);
    }

    public String getFormattedFreeSpace() {
        return formatBytes(freeSpace);
    }

    public String getFormattedUsedSpace() {
        return formatBytes(usedSpace);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char unit = "KMGTP".charAt(exp - 1);
        double value = bytes / Math.pow(1024, exp);
        return String.format("%.1f %sB", value, unit);
    }

    @Override
    public String toString() {
        return String.format("%s: %s / %s (%.1f%%)",
                name, getFormattedUsedSpace(), getFormattedTotalSpace(), usagePercent);
    }
}