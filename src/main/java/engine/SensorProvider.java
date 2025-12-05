package engine;

import java.util.List;

public interface SensorProvider {
    CpuInfo getCpuInfo();
    RamInfo getRamInfo();
    GpuInfo getGpuInfo();
    List<DiskInfo> getDisksInfo();
}