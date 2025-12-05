package engine;

import java.util.List;

public class SystemInfoService {
    private final SensorProvider provider;

    public SystemInfoService(SensorProvider provider) {
        this.provider = provider;
    }

    public CpuInfo readCpu() {
        return provider.getCpuInfo();
    }

    public RamInfo readRam() {
        return provider.getRamInfo();
    }

    public GpuInfo readGpu() {
        return provider.getGpuInfo();
    }

    public List<DiskInfo> readDisks() {
        return provider.getDisksInfo();
    }

    public static SystemInfoService createDefault() {
        return new SystemInfoService(new OshiSensorProvider());
    }
}