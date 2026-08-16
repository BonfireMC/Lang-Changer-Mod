package ua.bonfiremc.slc;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Optional;

public class SLCLanguage extends Language {
    private final Map<String, String> storage;

    public SLCLanguage(Map<String, String> storage) {
        this.storage = storage;
    }

    @Override
    public @NonNull String getOrDefault(@NonNull String elementId, @NonNull String defaultValue) {
        return this.storage.getOrDefault(elementId, defaultValue);
    }

    @Override
    public boolean has(@NonNull String elementId) {
        return this.storage.containsKey(elementId);
    }


    public boolean isDefaultRightToLeft() {
        return false;
    }

    public @NonNull FormattedCharSequence getVisualOrder(@NonNull FormattedText logicalOrderText) {
        return (output) -> logicalOrderText.visit((style, contents) -> StringDecomposer.iterateFormatted(contents, style, output) ? Optional.empty() : FormattedText.STOP_ITERATION, Style.EMPTY).isPresent();
    }
}
