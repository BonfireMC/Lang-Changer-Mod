package ua.bonfiremc.slc;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class SLCInitializer implements ModInitializer {
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_LOCALE = new DynamicCommandExceptionType(object ->
        Component.translatableEscape("commands.slc.failed.unknown", object)
    );

    public static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        LanguageLoader.fetchLocales();

        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> {
            dispatcher.register(literal("slc")
                .then(argument("locale", StringArgumentType.word())
                    .suggests((_, builder) -> SharedSuggestionProvider.suggest(LanguageLoader.LOCALE_TO_HASH.keySet(), builder))
                    .executes(context -> {
                        String locale = fixLocale(StringArgumentType.getString(context, "locale").toLowerCase());

                        if (!LanguageLoader.LOCALE_TO_HASH.containsKey(locale)) {
                            throw ERROR_UNKNOWN_LOCALE.create(locale);
                        }

                        if (locale.equals(LanguageLoader.getCurrentLanguage())) {
                            context.getSource().sendFailure(Component.translatable("commands.slc.failed.already_set"));
                            return 0;
                        }

                        context.getSource().sendSystemMessage(Component.translatable("commands.slc.setting", locale));

                        CompletableFuture.runAsync(() -> {
                            try {
                                LanguageLoader.setCurrentLanguage(locale);

                                Language newLang = LanguageLoader.loadAndCreateLanguage(locale);
                                Language.inject(newLang);

                                context.getSource().sendSuccess(() -> Component.literal("Language successfully switched to: \"" + locale + "\"!"), true);
                            } catch (Exception e) {
                                context.getSource().sendFailure(Component.translatable("commands.slc.failed.exception"));

                                LOGGER.error("Failed to set locale", e);
                            }
                        });
                        return 1;
                    })
                )
            );
        });
    }

    public static String fixLocale(String locale) {
        if (locale.equals("ru_ru") || locale.equals("rpr")) {
            return "uk_ua";
        } else {
            return locale;
        }
    }
}