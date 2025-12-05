package engine;

import oshi.SystemInfo;
import oshi.hardware.*;
import oshi.software.os.OSFileStore;
import com.profesorfalken.jsensors.JSensors;
import com.profesorfalken.jsensors.model.components.Components;
import com.profesorfalken.jsensors.model.components.Cpu;
import com.profesorfalken.jsensors.model.components.Gpu;
import com.profesorfalken.jsensors.model.sensors.Load;
import com.profesorfalken.jsensors.model.sensors.Temperature;

import java.util.ArrayList;
import java.util.List;

public class OshiSensorProvider implements SensorProvider {
    private final SystemInfo systemInfo = new SystemInfo();
    private final HardwareAbstractionLayer hardware = systemInfo.getHardware();
    private final CentralProcessor processor = hardware.getProcessor();
    private final Sensors sensors = hardware.getSensors();

    private long[] prevCpuTicks;

    private final String cachedCpuName;
    private final int cachedCpuCores;
    private final double cachedCpuClock;

    private GpuInfo cachedGpuInfo = null;
    private long lastGpuUpdateTime = 0;
    private static final long GPU_CACHE_TIME_MS = 400;

    public OshiSensorProvider() {
        prevCpuTicks = processor.getSystemCpuLoadTicks();
        cachedCpuName = processor.getProcessorIdentifier().getName();
        cachedCpuCores = processor.getPhysicalProcessorCount();
        cachedCpuClock = processor.getProcessorIdentifier().getVendorFreq() / 1_000_000.0;
    }

    @Override
    public CpuInfo getCpuInfo() {
        CpuInfo info = new CpuInfo();
        long[] currentTicks = processor.getSystemCpuLoadTicks();
        double cpuLoad = processor.getSystemCpuLoadBetweenTicks(prevCpuTicks) * 100;
        prevCpuTicks = currentTicks;

        info.setUsage(cpuLoad);
        info.setName(cachedCpuName);
        info.setBaseClock(cachedCpuClock);
        info.setCores(cachedCpuCores);

        double temp = sensors.getCpuTemperature();
        if (temp <= 0 || Double.isNaN(temp)) {
            temp = getCpuTempFromJSensors();
        }
        info.setTemperature(temp);
        return info;
    }

    private double getCpuTempFromJSensors() {
        try {
            Components components = JSensors.get.components();
            if (components.cpus != null) {
                for (Cpu cpu : components.cpus) {
                    if (cpu.sensors != null && cpu.sensors.temperatures != null) {
                        for (Temperature t : cpu.sensors.temperatures) {
                            if (t.value > 0) {
                                return t.value;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) { }
        return 0.0;
    }

    @Override
    public RamInfo getRamInfo() {
        RamInfo info = new RamInfo();
        GlobalMemory memory = hardware.getMemory();
        info.setTotal(memory.getTotal());
        info.setFree(memory.getAvailable());
        return info;
    }

    @Override
    public GpuInfo getGpuInfo() {
        long now = System.currentTimeMillis();
        if (cachedGpuInfo != null && (now - lastGpuUpdateTime) < GPU_CACHE_TIME_MS) {
            return cachedGpuInfo;
        }

        GpuInfo info = new GpuInfo();
        try {
            Components components = JSensors.get.components();
            String gpuName = "GPU";
            double gpuLoad = 0.0;
            double gpuTemp = 0.0;

            if (components.gpus != null && !components.gpus.isEmpty()) {
                Gpu gpu = components.gpus.get(0);
                gpuName = gpu.name != null ? gpu.name : "GPU";

                if (gpu.sensors != null) {
                    if (gpu.sensors.loads != null) {
                        for (Load load : gpu.sensors.loads) {
                            String name = load.name.toLowerCase();
                            if (name.contains("load") || name.contains("usage") || name.contains("core")) {
                                gpuLoad = load.value;
                                break;
                            }
                        }
                    }
                    if (gpu.sensors.temperatures != null) {
                        for (Temperature t : gpu.sensors.temperatures) {
                            if (t.value > 0) {
                                gpuTemp = t.value;
                                break;
                            }
                        }
                    }
                }
            } else {
                List<GraphicsCard> cards = hardware.getGraphicsCards();
                if (!cards.isEmpty()) {
                    gpuName = cards.get(0).getName();
                }
            }

            info.setName(gpuName);
            info.setGpuLoad(gpuLoad);
            info.setTemperature(gpuTemp);

            List<GraphicsCard> cards = hardware.getGraphicsCards();
            if (!cards.isEmpty()) {
                long vram = cards.get(0).getVRam();
                if (vram > 0) {
                    info.setVramTotal(vram);
                    info.setVramUsed((long)(vram * (gpuLoad / 100.0)));
                }
            }
        } catch (Exception e) {
            info.setName("GPU");
        }

        cachedGpuInfo = info;
        lastGpuUpdateTime = now;
        return info;
    }

    @Override
    public List<DiskInfo> getDisksInfo() {
        List<DiskInfo> disks = new ArrayList<>();
        List<OSFileStore> fileStores = systemInfo.getOperatingSystem().getFileSystem().getFileStores();

        for (OSFileStore fs : fileStores) {
            if (fs.getTotalSpace() > 0) {
                DiskInfo disk = new DiskInfo();
                String name = fs.getName();
                String mount = fs.getMount();
                String mountClean = mount.replace("\\", "").replace("/", "");

                if (name.contains(mountClean)) {
                    disk.setName(name);
                } else {
                    disk.setName(name + " (" + mount + ")");
                }

                disk.setTotalSpace(fs.getTotalSpace());
                disk.setFreeSpace(fs.getUsableSpace());
                disks.add(disk);
            }
        }
        return disks;
    }
}