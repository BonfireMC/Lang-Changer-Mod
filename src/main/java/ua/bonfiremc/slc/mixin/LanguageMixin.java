package ua.bonfiremc.slc.mixin;

import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.bonfiremc.slc.LangChanger;
import ua.bonfiremc.slc.LangManager;

import java.io.IOException;

@Mixin(Language.class)
public class LanguageMixin {
    @Inject(method = "loadDefault", at = @At("HEAD"), cancellable = true)
    private static void onLoadDefaultLanguage(CallbackInfoReturnable<Language> cir) {
        String langCode = LangManager.getCurrentLanguage();
        if (langCode.equals("ru_ru")) {
            LangChanger.LOGGER.info("Eeeewww dude. Changing to uk_ua...");
            try {
                LangManager.setCurrentLanguage("uk_ua");
            } catch (IOException e) {
                LangChanger.LOGGER.error("Cannot overwrite \"currentlang.txt\"", e);
            }
            langCode = "uk_ua";
        }
        LangChanger.LOGGER.info("Local lang initializing: " + langCode);
        Language customLanguage = LangManager.loadAndCreateLanguage(langCode);
        cir.setReturnValue(customLanguage);
    }
}