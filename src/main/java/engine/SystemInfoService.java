package engine;

public class SystemInfoService {

    private final SensorProvider provider;

    public SystemInfoService(SensorProvider provider) {
        this.provider = provider;
    }

    public CpuInfo readCpu() throws InterruptedException {
        return provider.getCpuInfo();
    }

    public RamInfo readRam() {
        return provider.getRamInfo();
    }

    // Просто добавляем метод для GPU
    public GpuInfo readGpu() throws InterruptedException {
        return provider.getGpuInfo();
    }
}