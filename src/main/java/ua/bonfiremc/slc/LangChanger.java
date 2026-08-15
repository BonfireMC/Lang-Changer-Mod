package ua.bonfiremc.slc;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class LangChanger implements ModInitializer {
    private static final Component LOC_TEST = Component.translatable("test");
    private static final Component LANGCHANGER_START = Component.translatable("message.langchanger.start");
    private static final Component LANG_SETTING_SUCCESS = Component.translatable("message.langchanger.lang_setting.success");
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final List<String> LANGS = List.of("af_za", "ar_sa", "ast_es", "az_az", "ba_ru", "bar", "be_by", "be_latn", "bg_bg", "br_fr", "brb", "bs_ba", "ca_es", "cv_cu", "cs_cz", "cy_gb", "da_dk", "de_at", "de_ch", "de_de", "el_gr", "en_au", "en_ca", "en_gb", "en_nz", "en_pt", "en_ud", "en_us", "enp", "enws", "eo_uy", "es_ar", "es_cl", "es_ec", "es_es", "es_mx", "es_uy", "es_ve", "esan", "et_ee", "eu_es", "fa_ir", "fi_fi", "fil_ph", "fo_fo", "fr_ca", "fr_ch", "fr_fr", "fra_de", "fur_it", "fy_nl", "ga_ie", "gd_gb", "gl_es", "go_fr", "got_de", "hal_ua", "haw_us", "he_il", "hi_in", "hn_no", "hr_hr", "hu_hu", "hy_am", "id_id", "ig_ng", "io_en", "is_is", "isv", "it_it", "ja_jp", "jbo_en", "ka_ge", "kk_kz", "kn_in", "ko_kr", "ksh", "kw_gb", "ky_kg", "la_la", "lb_lu", "li_li", "lmo", "lo_la", "lol_us", "lt_lt", "lv_lv", "lzh", "mk_mk", "mn_mn", "ms_my", "mt_mt", "nah", "nds_de", "nl_be", "nl_nl", "nn_no", "no_no", "oc_fr", "ovd", "pl_pl", "pls", "pt_br", "pt_pt", "qcb_es", "qid", "qya_aa", "ro_ro", "rpr", "ry_ua", "sah_sah", "se_no", "sk_sk", "sl_si", "so_so", "sq_al", "sr_cs", "sr_sp", "sv_se", "sxu", "szl", "ta_in", "th_th", "tl_ph", "tlh_aa", "tok", "tr_tr", "tt_ru", "tzo_mx", "uk_ua", "uz_uz", "val_es", "vec_it", "vro", "vi_vn", "vp_vl", "yi_de", "yo_ng", "zh_cn", "zh_hk", "zh_tw", "zlm_arab");

    @Override
    public void onInitialize() {
        LOGGER.info("Mod \"Langchanger\" initialized");

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("setlang")
                .then(argument("lang", StringArgumentType.word())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(LANGS, builder))
                    .executes(context -> {
                        String langCode = StringArgumentType.getString(context, "lang").toLowerCase();
                        if (langCode.equals("ru_ru")) {
                            LOGGER.info("Eeeewww dude.");
                            return 0;
                        }
                        if (!LANGS.contains(langCode)) {
                            LOGGER.info("Error: Unknown langiage - \"" + langCode + "\".");
                            return 0;
                        }
                        if (langCode.equals(LangManager.getCurrentLanguage())) {
                            LOGGER.info("Um. You are literally already in this language dude :|");
                            return 0;
                        }
                        LOGGER.info("Language setting: \"" + langCode + "\"...");
                        CompletableFuture.runAsync(() -> {
                            try {
                                LangManager.setCurrentLanguage(langCode);
                                Language newLang = LangManager.loadAndCreateLanguage(langCode);
                                Language.inject(newLang);
                                context.getSource().sendSuccess(() -> Component.literal("Language successfully switched to: \"" + langCode + "\"!"), true);
                            } catch (Exception e) {
                                e.printStackTrace();
                                LOGGER.info("Error in language switching.");
                            }
                        });
                        return 1;
                    })
                )
            );
        });
    }
}