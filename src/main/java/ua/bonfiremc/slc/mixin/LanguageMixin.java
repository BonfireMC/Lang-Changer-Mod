package ua.bonfiremc.slc.mixin;

import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.bonfiremc.slc.SLCInitializer;
import ua.bonfiremc.slc.LanguageLoader;

import java.io.IOException;

@Mixin(Language.class)
public class LanguageMixin {
    @Inject(method = "loadDefault", at = @At("HEAD"), cancellable = true)
    private static void slc$loadDefault(CallbackInfoReturnable<Language> cir) {
        String langCode = LanguageLoader.getCurrentLanguage();
        if (langCode.equals("ru_ru")) {
            SLCInitializer.LOGGER.info("Eeeewww dude. Changing to uk_ua...");
            try {
                LanguageLoader.setCurrentLanguage("uk_ua");
            } catch (IOException e) {
                SLCInitializer.LOGGER.error("Cannot overwrite \"currentlang.txt\"", e);
            }
            langCode = "uk_ua";
        }
        SLCInitializer.LOGGER.info("Local lang initializing: " + langCode);
        Language customLanguage = LanguageLoader.loadAndCreateLanguage(langCode);
        cir.setReturnValue(customLanguage);
    }
}