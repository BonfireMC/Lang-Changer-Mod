package ua.bonfiremc.slc;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class SLC implements ModInitializer {
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final DynamicCommandExceptionType ERROR_UNKNOWN_LANGUAGE = new DynamicCommandExceptionType(object ->
        Component.translatableEscape("commands.slc.failed.unknown", object)
    );

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> {
            dispatcher.register(literal("slc")
                .then(argument("language", StringArgumentType.word())
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                    .suggests((_, builder) -> {
                        builder.suggest(Language.DEFAULT);

                        for (RemoteLanguageData language : LanguageLoader.INSTANCE.remoteLanguages) {
                            builder.suggest(language.key);
                        }

                        return builder.buildFuture();
                    })
                    .executes(context -> {
                        String languageKey = normalizeLang(StringArgumentType.getString(context, "language").toLowerCase());
                        String currentLanguage = LanguageLoader.INSTANCE.getCurrentLanguage();

                        if (languageKey.equals(currentLanguage)) {
                            context.getSource().sendFailure(Component.translatable("commands.slc.failed.already_set"));
                            return 0;
                        }

                        RemoteLanguageData remote = LanguageLoader.INSTANCE.findRemoteData(languageKey);

                        if (remote == null && !languageKey.equals(Language.DEFAULT)) {
                            throw ERROR_UNKNOWN_LANGUAGE.create(languageKey);
                        }

                        context.getSource().sendSystemMessage(Component.translatable("commands.slc.setting", languageKey));

                        CompletableFuture.runAsync(() -> {
                            try {
                                LanguageLoader.INSTANCE.inject(languageKey);

                                context.getSource().sendSuccess(() -> Component.translatable("commands.slc.success", languageKey), true);
                            } catch (Exception e) {
                                LOGGER.error("Failed to set language", e);

                                context.getSource().sendFailure(Component.translatable("commands.slc.failed.exception"));
                            }
                        });
                        return 1;
                    })
                )
            );
        });
    }

    public static String normalizeLang(String language) {
        if (language.equals("ru_ru") || language.equals("rpr")) {
            return "uk_ua";
        } else {
            return language;
        }
    }
}
