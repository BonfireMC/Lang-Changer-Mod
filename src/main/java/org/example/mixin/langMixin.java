package org.example.mixin;

import net.minecraft.locale.Language;
import org.example.langmanager;
import org.example.langchanger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;

@Mixin(Language.class)
public class langMixin {
    @Inject(method = "loadDefault", at = @At("HEAD"), cancellable = true)
    private static void onLoadDefaultLanguage(CallbackInfoReturnable<Language> cir) {
        String langCode = langmanager.getCurrentLanguage();
        if (langCode.equals("ru_ru")) {
            langchanger.LOGGER.info("Eeeewww dude. Changing to uk_ua...");
            try{
                langmanager.setCurrentLanguage("uk_ua");
            } catch (IOException e) {
                langchanger.LOGGER.error("Cannot overwrite \"currentlang.txt\"", e);
            }
            langCode = "uk_ua";
        }
        langchanger.LOGGER.info("Local lang initializing: " + langCode);
        Language customLanguage = langmanager.loadAndCreateLanguage(langCode);
        cir.setReturnValue(customLanguage);
    }
}