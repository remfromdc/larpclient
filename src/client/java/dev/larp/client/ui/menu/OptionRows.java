package dev.larp.client.ui.menu;

import net.minecraft.client.OptionInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Supplier;

/**
 * Binds vanilla {@link OptionInstance}s to Larp menu rows.
 *
 * <p>The instances are used raw (their generic signatures erase to
 * {@code Object} under reflection-safe access) and dispatched on the runtime
 * value set: booleans become {@link MenuToggle}s, slider value sets become
 * {@link MenuSlider}s and cyclable sets become {@link MenuCycle}s. Labels
 * stay short and flat to match the title screen instead of reusing vanilla's
 * long "Name: value" captions.</p>
 */
public final class OptionRows {
    private OptionRows() {
    }

    /** How a slider's normalized position maps back to a readout string. */
    public record SliderFormat(DoubleUnaryOperator realValue, java.util.function.Function<Double, String> text) {
        public static SliderFormat plain(String suffix) {
            return new SliderFormat(v -> v, v -> number(v) + suffix);
        }

        public static SliderFormat percent() {
            return new SliderFormat(v -> v * 100.0, v -> Math.round(v * 100.0) + "%");
        }

        private static String number(double value) {
            if (Math.abs(value - Math.rint(value)) < 0.0001 && Math.abs(value) < 1_000_000) {
                return String.valueOf((long) Math.rint(value));
            }
            return String.valueOf(Math.round(value * 10.0) / 10.0);
        }
    }

    public static MenuWidget bind(Supplier<OptionInstance> source, String label, float textSize,
                                  int delay, SliderFormat slider, List<String> labels) {
        OptionInstance instance = source.get();
        Object value = instance.get();
        OptionInstance.ValueSet set = instance.values();
        if (value instanceof Boolean) {
            return new MenuToggle(0, 0, 1, 1, label, textSize, delay,
                    () -> (Boolean) instance.get(),
                    next -> instance.set(next));
        }
        if (set instanceof OptionInstance.SliderableValueSet sliderSet) {
            boolean cycle = set instanceof OptionInstance.SliderableOrCyclableValueSet both
                    && both.createCycleButton();
            if (!cycle) return sliderRow(instance, sliderSet, label, textSize, delay, slider);
            return cycleRowStepper(instance, sliderSet, label, textSize, delay, labels);
        }
        if (set instanceof OptionInstance.CycleableValueSet cycleSet) {
            return cycleRow(instance, cycleSet, label, textSize, delay, labels);
        }
        // Last resort: a non-functional label row so unknown sets stay visible.
        return new MenuButton(0, 0, 1, 1, label, textSize, delay, () -> { });
    }

    public static MenuWidget bind(Supplier<OptionInstance> source, String label, float textSize, int delay) {
        return bind(source, label, textSize, delay, SliderFormat.plain(""), null);
    }

    private static MenuSlider sliderRow(OptionInstance instance, OptionInstance.SliderableValueSet set,
                                        String label, float textSize, int delay, SliderFormat format) {
        java.util.function.Function<Double, String> readout = slider -> {
            Object real = set.fromSliderValue(slider);
            double value = real instanceof Number number ? number.doubleValue() : slider;
            return format.text().apply(format.realValue().applyAsDouble(value));
        };
        return new MenuSlider(0, 0, 1, 1, label, textSize, delay,
                () -> set.toSliderValue(instance.get()),
                slider -> instance.set(set.fromSliderValue(slider)),
                readout);
    }

    private static MenuCycle cycleRow(OptionInstance instance, OptionInstance.CycleableValueSet set,
                                      String label, float textSize, int delay, List<String> labels) {
        List<Object> values = selectedValues(set);
        List<String> names = labels != null && labels.size() == values.size()
                ? labels
                : values.stream().map(OptionRows::displayName).toList();
        return new MenuCycle(0, 0, 1, 1, label, textSize, delay, names,
                () -> Math.max(0, values.indexOf(instance.get())),
                picked -> {
                    if (picked >= 0 && picked < values.size()) instance.set(values.get(picked));
                });
    }

    /**
     * Cycles a slider-capable set (for example render distance) through its
     * discrete steps instead of showing a drag slider.
     */
    private static MenuCycle cycleRowStepper(OptionInstance instance, OptionInstance.SliderableValueSet set,
                                             String label, float textSize, int delay, List<String> labels) {
        List<Object> values = steppedValues(instance, set);
        List<String> names = labels != null && labels.size() == values.size()
                ? labels
                : values.stream().map(OptionRows::displayName).toList();
        return new MenuCycle(0, 0, 1, 1, label, textSize, delay, names,
                () -> Math.max(0, values.indexOf(instance.get())),
                picked -> {
                    if (picked >= 0 && picked < values.size()) instance.set(values.get(picked));
                });
    }

    /** Walks next() from the minimum until it repeats, collecting each step. */
    private static List<Object> steppedValues(OptionInstance instance, OptionInstance.SliderableValueSet set) {
        List<Object> values = new ArrayList<>();
        try {
            Object first = instance.get();
            values.add(first);
            for (int i = 0; i < 64; i++) {
                Optional<?> option = set.next(values.get(values.size() - 1));
                if (option.isEmpty() || values.contains(option.get())) break;
                values.add(option.get());
            }
            if (values.size() > 1) return values;
        } catch (RuntimeException ignored) {
        }
        return List.of(instance.get());
    }

    @SuppressWarnings("unchecked")
    private static List<Object> selectedValues(OptionInstance.CycleableValueSet set) {
        try {
            List<Object> list = new ArrayList<>((List<Object>) set.valueListSupplier().getSelectedList());
            if (!list.isEmpty()) return list;
        } catch (RuntimeException ignored) {
        }
        try {
            List<Object> list = new ArrayList<>((List<Object>) set.valueListSupplier().getDefaultList());
            if (!list.isEmpty()) return list;
        } catch (RuntimeException ignored) {
        }
        return List.of();
    }

    private static String displayName(Object value) {
        if (value instanceof Enum<?> constant) return MenuStyle.prettify(constant.name());
        if (value instanceof Boolean bool) return bool ? "On" : "Off";
        return MenuStyle.prettify(String.valueOf(value));
    }

    /** Cycles the instance once (used by rows that only need next). */
    public static void next(OptionInstance instance) {
        if (instance.values() instanceof OptionInstance.SliderableValueSet set) {
            Optional<?> option = set.next(instance.get());
            option.ifPresent(instance::set);
        }
    }
}
