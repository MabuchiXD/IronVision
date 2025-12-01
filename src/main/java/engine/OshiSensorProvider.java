package engine;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import com.profesorfalken.jsensors.JSensors;
import com.profesorfalken.jsensors.model.components.Components;
import com.profesorfalken.jsensors.model.components.Gpu;
import com.profesorfalken.jsensors.model.sensors.Load;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class OshiSensorProvider implements SensorProvider {

    private final SystemInfo si = new SystemInfo();
    private final HardwareAbstractionLayer hal = si.getHardware();
    private final CentralProcessor cpu = hal.getProcessor();
    private long[] prevTicks = cpu.getSystemCpuLoadTicks();

    @Override
    public CpuInfo getCpuInfo() {
        CpuInfo info = new CpuInfo();

        // CPU LOAD
        long[] oldTicks = prevTicks;
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        prevTicks = cpu.getSystemCpuLoadTicks();
        double load = cpu.getSystemCpuLoadBetweenTicks(oldTicks) * 100;
        info.usage = Math.min(100.0, Math.max(0.0, load));

        // BASE CLOCK
        info.baseClock = cpu.getProcessorIdentifier().getVendorFreq() / 1_000_000.0;

        return info;
    }

    @Override
    public RamInfo getRamInfo() {
        GlobalMemory mem = hal.getMemory();
        RamInfo info = new RamInfo();
        info.total = mem.getTotal();
        info.free = mem.getAvailable();
        info.used = info.total - info.free;
        return info;
    }

    @Override
    public GpuInfo getGpuInfo() {
        GpuInfo info = new GpuInfo();

        try {
            // Используем jSensors для получения данных GPU
            Components components = JSensors.get.components();

            if (components.gpus != null && !components.gpus.isEmpty()) {
                Gpu gpu = components.gpus.get(0);
                info.name = gpu.name;

                // Получаем загрузку GPU
                if (gpu.sensors.loads != null && !gpu.sensors.loads.isEmpty()) {
                    Load load = gpu.sensors.loads.get(0);
                    info.gpuLoad = load.value;
                } else {
                    info.gpuLoad = 0.0;
                }

                // Получаем VRAM через системную команду (для Windows)
                long vramTotal = getVramFromSystem();
                info.vramTotal = vramTotal;
                info.vramUsed = (long)(vramTotal * (info.gpuLoad / 100.0));

            } else {
                // Fallback: если jSensors не нашел GPU
                info.name = getGpuNameFromSystem();
                long vramTotal = getVramFromSystem();
                info.vramTotal = vramTotal;
                info.gpuLoad = getEstimatedGpuLoad();
                info.vramUsed = (long)(vramTotal * (info.gpuLoad / 100.0));
            }

        } catch (Exception e) {
            System.err.println("Ошибка получения GPU информации: " + e.getMessage());
            // Fallback на базовую информацию
            long vramTotal = getVramFromSystem();
            info.name = getSimpleGpuInfo();
            info.vramTotal = vramTotal;
            info.gpuLoad = getEstimatedGpuLoad();
            info.vramUsed = (long)(vramTotal * (info.gpuLoad / 100.0));
        }

        return info;
    }

    private long getVramFromSystem() {
        String os = System.getProperty("os.name").toLowerCase();

        try {
            if (os.contains("win")) {
                return getWindowsVram();
            } else if (os.contains("linux")) {
                return getLinuxVram();
            }
        } catch (Exception e) {
            System.err.println("Ошибка получения VRAM: " + e.getMessage());
        }

        // Значение по умолчанию
        return 4L * 1024 * 1024 * 1024; // 4 GB
    }

    private long getWindowsVram() {
        // Для Windows используем wmic
        try {
            Process process = Runtime.getRuntime().exec(
                    new String[]{"cmd", "/c", "wmic path win32_VideoController get AdapterRAM"}
            );

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.equalsIgnoreCase("AdapterRAM")) {
                    try {
                        // Значение в байтах
                        return Long.parseLong(line);
                    } catch (NumberFormatException e) {
                        // Пустое тело - просто игнорируем некорректные строки
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Ошибка получения VRAM Windows: " + e.getMessage());
        }

        // Если не получили, возвращаем значение по умолчанию
        return 4L * 1024 * 1024 * 1024;
    }

    private long getLinuxVram() {
        // Для Linux пробуем несколько способов
        long vram = tryGlxInfo();
        if (vram > 0) {
            return vram;
        }

        vram = tryLspci();
        if (vram > 0) {
            return vram;
        }

        return 2L * 1024 * 1024 * 1024; // 2 GB по умолчанию для Linux
    }

    private long tryGlxInfo() {
        try {
            Process process = Runtime.getRuntime().exec("glxinfo | grep -i 'video memory'");
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            String line = reader.readLine();
            if (line != null && line.toLowerCase().contains("video memory")) {
                String[] parts = line.split(":");
                if (parts.length > 1) {
                    String memStr = parts[1].replaceAll("[^0-9]", "");
                    if (!memStr.isEmpty()) {
                        long mb = Long.parseLong(memStr);
                        return mb * 1024 * 1024; // MB to bytes
                    }
                }
            }
        } catch (Exception e) {
            // Игнорируем и пробуем следующий способ
        }
        return 0;
    }

    private long tryLspci() {
        try {
            Process process = Runtime.getRuntime().exec("lspci -v | grep -A 10 -i vga");
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.toLowerCase().contains("memory") && line.contains("[")) {
                    int start = line.indexOf("[size=");
                    int end = line.indexOf("]", start);
                    if (start != -1 && end != -1) {
                        String sizeStr = line.substring(start + 6, end);
                        if (sizeStr.endsWith("M")) {
                            long mb = Long.parseLong(sizeStr.replace("M", ""));
                            return mb * 1024 * 1024;
                        } else if (sizeStr.endsWith("G")) {
                            long gb = Long.parseLong(sizeStr.replace("G", ""));
                            return gb * 1024 * 1024 * 1024;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Игнорируем
        }
        return 0;
    }

    private String getGpuNameFromSystem() {
        try {
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("win")) {
                Process process = Runtime.getRuntime().exec(
                        new String[]{"cmd", "/c", "wmic path win32_VideoController get name"}
                );

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream())
                );

                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.equalsIgnoreCase("name")) {
                        return line;
                    }
                }
            } else if (os.contains("linux")) {
                Process process = Runtime.getRuntime().exec("lspci | grep -i vga");
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream())
                );

                String line = reader.readLine();
                if (line != null) {
                    // Пример: 00:02.0 VGA compatible controller: Intel Corporation HD Graphics 630
                    String[] parts = line.split(":");
                    if (parts.length > 2) {
                        return parts[2].trim();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Ошибка получения имени GPU: " + e.getMessage());
        }
        return "Видеокарта";
    }

    private String getSimpleGpuInfo() {
        // Базовая информация о GPU
        try {
            String vendor = System.getProperty("sun.java2d.gpuvendor", "");
            String renderer = System.getProperty("sun.java2d.gpudriver", "");

            if (!renderer.isEmpty()) {
                return renderer;
            } else if (!vendor.isEmpty()) {
                return "GPU от " + vendor;
            }
        } catch (Exception e) {
            // Игнорируем
        }
        return "Интегрированная видеокарта";
    }

    private double getEstimatedGpuLoad() {
        // Эмуляция загрузки GPU (если не получили реальную)
        return 15.0; // 15% по умолчанию
    }

    @SuppressWarnings("unused")
    private void printDebugInfo() {
        System.out.println("=== SYSTEM DEBUG INFO ===");
        System.out.println("CPU: " + cpu.getProcessorIdentifier().getName());
        System.out.println("Cores: " + cpu.getPhysicalProcessorCount() + " physical, " +
                cpu.getLogicalProcessorCount() + " logical");

        try {
            Components components = JSensors.get.components();
            System.out.println("jSensors GPUs found: " +
                    (components.gpus != null ? components.gpus.size() : 0));

            if (components.gpus != null && !components.gpus.isEmpty()) {
                for (Gpu gpu : components.gpus) {
                    System.out.println("GPU: " + gpu.name);
                    if (gpu.sensors.loads != null) {
                        System.out.println("  Load sensors: " + gpu.sensors.loads.size());
                        for (Load load : gpu.sensors.loads) {
                            System.out.println("    Load: " + load.name + " = " + load.value);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("jSensors error: " + e.getMessage());
        }

        System.out.println("VRAM (определено): " + (getVramFromSystem() / 1024 / 1024) + " MB");
        System.out.println("========================\n");
    }
}