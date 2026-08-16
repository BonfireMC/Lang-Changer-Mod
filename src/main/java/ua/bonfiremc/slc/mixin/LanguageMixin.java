package ua.bonfiremc.slc.mixin;

import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.bonfiremc.slc.LanguageLoader;

@Mixin(Language.class)
public class LanguageMixin {
    @Inject(method = "loadDefault", at = @At("HEAD"), cancellable = true)
    private static void slc$loadDefault(CallbackInfoReturnable<Language> cir) {
        cir.setReturnValue(LanguageLoader.INSTANCE.createLanguage());
    }
}
