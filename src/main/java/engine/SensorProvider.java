package engine;

public interface SensorProvider {
    CpuInfo getCpuInfo() throws InterruptedException;
    RamInfo getRamInfo();
    GpuInfo getGpuInfo() throws InterruptedException; // добавляем
}