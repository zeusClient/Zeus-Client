package com.zlorbie788.zeus.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zlorbie788.zeus.ui.KnifeHD;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Zeus Crates: the knives you own, the keys you hold, Zeus tokens earned by playing, and promo codes.
 * Keys cost {@link #KEY_PRICE} tokens and any crate opens with {@link #KEYS_PER_OPEN} keys: there are no
 * crates to collect first (crates held from before are turned into keys when the save is read).
 * Everything is kept in config/zeus/crates.dat, signed so a hand-edited file is
 * noticed and reset rather than trusted.
 */
public final class Crates {
    private Crates() {
    }

    // ------------------------------------------------------------------ rarities
    public enum Rarity {
        MORTAL("Mortal", 0xFF4B69FF, 6000, 1),
        HEROIC("Heroic", 0xFF8847FF, 2500, 2),
        DIVINE("Divine", 0xFFD32CE6, 1000, 4),
        TITAN("Titan", 0xFFEB4B4B, 400, 8),
        OLYMPIAN("Olympian", 0xFFFFC83A, 100, 20),
        /** The secret tier: one hand-traced knife per crate, about 1 in 400 opens. Its colour runs through the rainbow. */
        CHROMA("Chroma", 0xFFFF5AD2, 25, 60);

        public final String label;
        public final int colour;
        /** Weight out of 10 000. */
        public final int weight;
        /** Tokens given back when the knife is already owned. */
        public final int refund;

        Rarity(String label, int colour, int weight, int refund) {
            this.label = label;
            this.colour = colour;
            this.weight = weight;
            this.refund = refund;
        }

        public String odds() {
            if (weight % 100 == 0) return (weight / 100) + "%";
            return String.format(Locale.ROOT, weight < 100 ? "%.2f%%" : "%.1f%%", weight / 100f);
        }

        /** The colour to draw this rarity in right now: Chroma cycles slowly through the rainbow, the rest are fixed. */
        public int tint() {
            if (this != CHROMA) return colour;
            float h = (System.nanoTime() % 6_000_000_000L) / 6e9f;
            return 0xFF000000 | (java.awt.Color.HSBtoRGB(h, 0.55f, 1f) & 0xFFFFFF);
        }
    }

    public record Item(String id, Rarity rarity) {
    }

    public static final class Crate {
        public final String id;
        public final String name;
        public final String tagline;
        public final int colour;
        public final int dark;
        public final List<Item> items = new ArrayList<>();

        Crate(String id, String name, String tagline, int colour, int dark) {
            this.id = id;
            this.name = name;
            this.tagline = tagline;
            this.colour = colour;
            this.dark = dark;
        }

        Crate add(Rarity r, String... ids) {
            for (String s : ids) items.add(new Item(s, r));
            return this;
        }

        public Item flagship() {
            for (Item it : items) if (it.rarity == Rarity.OLYMPIAN) return it;
            return items.get(0);
        }
    }

    public static final String STARTER = "karambit_stock";
    public static final int KEY_PRICE = 3;
    /** Keys one crate takes to open. */
    public static final int KEYS_PER_OPEN = 3;
    public static final long TOKEN_MS = 5 * 60_000L;
    public static final long DROP_MS = 90 * 60_000L;
    private static final long IDLE_MS = 90_000L;
    /** Active play needed each day before the daily streak reward is paid (so just launching does not count). */
    public static final long STREAK_PLAY_MS = 3 * 60_000L;
    /** Tokens for day 1, 2, 3 ... of a streak; day 7 and on pay the last value. */
    private static final int[] STREAK_REWARD = {2, 3, 4, 5, 6, 8, 10};

    public static final List<Crate> CRATES = List.of(
            new Crate("olympus", "Olympus Crate", "Marble, gold and the forge of the gods", 0xFFF5C04A, 0xFF3A2A08)
                    .add(Rarity.CHROMA, "chroma_phoenix")
                    .add(Rarity.OLYMPIAN, "bowie_olympus")
                    .add(Rarity.TITAN, "tac_olympus", "ursus_gold")
                    .add(Rarity.DIVINE, "tac_damascus", "m9_damascus", "skeleton_case")
                    .add(Rarity.HEROIC, "tac_tiger", "bowie_damascus", "tac_case")
                    .add(Rarity.MORTAL, "huntsman_case", "stiletto_damascus", "stiletto_case"),
            new Crate("tempest", "Tempest Crate", "Storm steel, charged with lightning", 0xFF5AA8FF, 0xFF0A1A38)
                    .add(Rarity.CHROMA, "chroma_crystal")
                    .add(Rarity.OLYMPIAN, "tac_lightning")
                    .add(Rarity.TITAN, "huntsman_storm", "karambit_sapphire")
                    .add(Rarity.DIVINE, "tac_doppler", "bowie_sapphire", "ursus_sapphire")
                    .add(Rarity.HEROIC, "tac_urban", "ursus_urban", "skeleton_night")
                    .add(Rarity.MORTAL, "m9_night", "huntsman_night", "stiletto_night"),
            new Crate("hades", "Hades Crate", "Forged in the underworld's fire", 0xFFFF4A4A, 0xFF3A0808)
                    .add(Rarity.CHROMA, "chroma_m9")
                    .add(Rarity.OLYMPIAN, "karambit_bloodstone")
                    .add(Rarity.TITAN, "karambit_inferno", "bowie_inferno")
                    .add(Rarity.DIVINE, "karambit_crimson", "tac_ruby", "m9_ruby")
                    .add(Rarity.HEROIC, "tac_crimson", "karambit_slaughter", "huntsman_crimson")
                    .add(Rarity.MORTAL, "tac_slaughter", "butterfly_slaughter", "bowie_slaughter"),
            new Crate("nyx", "Nyx Crate", "Night, nebulae and things that shimmer", 0xFFB06AFF, 0xFF1E0A3A)
                    .add(Rarity.CHROMA, "chroma_karambit")
                    .add(Rarity.OLYMPIAN, "karambit_nebula")
                    .add(Rarity.TITAN, "m9_phantom", "ursus_nebula")
                    .add(Rarity.DIVINE, "skeleton_phantom", "butterfly_ultraviolet", "karambit_fade")
                    .add(Rarity.HEROIC, "tac_ultraviolet", "butterfly_fade", "bowie_fade")
                    .add(Rarity.MORTAL, "tac_fade", "skeleton_fade", "stiletto_fade"),
            new Crate("poseidon", "Poseidon Crate", "Pulled up from the deep", 0xFF2AD0D0, 0xFF052A30)
                    .add(Rarity.CHROMA, "chroma_butterfly")
                    .add(Rarity.OLYMPIAN, "skeleton_abyss")
                    .add(Rarity.TITAN, "m9_frost", "ursus_frost")
                    .add(Rarity.DIVINE, "stiletto_abyss", "tac_emerald", "butterfly_emerald")
                    .add(Rarity.HEROIC, "skeleton_emerald", "huntsman_urban", "huntsman_tiger")
                    .add(Rarity.MORTAL, "m9_camo", "m9_case"),
            new Crate("titan", "Titan Crate", "Loud colours for those who want to be seen", 0xFFFF8A2A, 0xFF3A1A04)
                    .add(Rarity.CHROMA, "chroma_kunai")
                    .add(Rarity.OLYMPIAN, "butterfly_sunset")
                    .add(Rarity.TITAN, "butterfly_toxic", "karambit_tiger")
                    .add(Rarity.DIVINE, "butterfly_marble", "stiletto_sunset", "tac_marble")
                    .add(Rarity.HEROIC, "huntsman_toxic", "ursus_tiger", "skeleton_marble")
                    .add(Rarity.MORTAL, "stiletto_marble", "bowie_rust", "ursus_ruby"));

    public static Crate crate(String id) {
        for (Crate c : CRATES) if (c.id.equals(id)) return c;
        return null;
    }

    /** Which crate a knife drops from, or null for the starter. */
    public static Crate crateOf(String knifeId) {
        for (Crate c : CRATES) for (Item it : c.items) if (it.id.equals(knifeId)) return c;
        return null;
    }

    public static Rarity rarityOf(String knifeId) {
        for (Crate c : CRATES) for (Item it : c.items) if (it.id.equals(knifeId)) return it.rarity;
        return Rarity.MORTAL;
    }

    // ------------------------------------------------------------------ promo codes (only hashes ship)
    private static final String SALT = "zeus-crates/v1:";
    private static final String[][] CODES = {
            {"b24fdcaaeae2ba1c4b93870c6d9118dbbd875c0b55351b9183cfb4be73399f5e", "olympus:1"},
            {"6718e66764b532e44099122170a4264d27aca09f640028078bb433c61283586e", "tempest:1"},
            {"10f1232395c1faa8b2c7f33de56c22c653bb9cc61655ea0c6a8262a13a89c6d9", "hades:1"},
            {"4a275448bd08bafda5e3d938c5c8dab872c3ba2596c611bff6970bf6cc34c192", "nyx:1"},
            {"26aa4d5d61d2645cb585aa4804a9b67b55ed42fffc859b474f667ee48332de11", "poseidon:1"},
            {"4a16fad809b3ff796ec1e9368ea66a08bb0a1c5162a5a37603633093c927fd52", "titan:1"},
            {"ef1fc15f1f67d9bee21df5421c55483a48a7a39e86e06d64d129448746cd7552", "olympus:1"},
            {"b744fccf81280c30449110f0eea18e89dab36755640b12304e32d53b38553fcb", "tempest:1"},
            {"71cb0cc7ba03bdf0cb9443ef1900a71e864848afec968119f4baf4fc062ed823", "hades:1"},
            {"60c172655ac557267c6d4b0ad9eba73b80467b27ffbd6fd25494405f3fd58f1d", "nyx:1"},
            {"dc71e9afd97ed4f267bef12957b186aebc8497aae383b8622229b19f9d34b4fb", "poseidon:1"},
            {"eaf881392eae913d4c250426f2dfd4e7c4a60380b2620bd10ae5aa2fb659e5f4", "titan:1"},
            {"e83433e8a1e889d97f1cb38a501a92872ee7a6a37e1eb4a19c807d077d3ad497", "random:1"},
            {"6ae9bcae958a658df4ac4df32201f48735efd2cb4179264f5fdd9978018dffdf", "random:1"},
            {"8d3390d145905b700a65b56cd4f483ffb258509ce411a983f488e0129399bdcf", "random:1"},
            {"0620e5852b10f3290b5b7af616ea0abda4fc5d5bf3fab859272221e6cd0d9b17", "random:1"},
            {"dd39df5f91612c820228bb0c80eac09af53a9fe608d16b607e36b814548124ef", "random:1"},
            {"d996da80f9d799993ae8c10959bf5fc6cc28952be8929a01c271bbf0c56d034b", "random:1"},
            {"985f50665a8f4b0379c776e6e9f5f5bf036d58a04e7300df760712053e9114e9", "random:2"},
            {"1047049646f83291dc4d283a8c552e4003988b8675342c24a80f13847fb503de", "random:2"},
            {"c54a27f1a8963fb13f2e02c330615a6be86598f4fc54b4683a09a5e5cf17ad4d", "random:2"},
            {"c1f6e6ecde1405ffc3173ab2a3851e88761b071132743714af3bdcd38189a72f", "random:2"},
            {"d4504159ae8a279e806d6bb5c09ab6450f56576b08f8ccc8fdf4321cabc4add7", "olympus+hades+nyx:1"},
            {"7ea743b2a423332ca763c9aa3140974e3f3fce86acf0e95013534e90470f8eb8", "tempest+poseidon+titan:1"},
            {"05fde1635bc1871f37a4108d5889b2302e47724f36c74f148f2166240af51d19", "all:1"},
            {"94d23f92488f01c95b9c7990c59a1f1cd417d9c6052c614cfd327573da8d668c", "nyx:1"},
            {"91babd45f783fb38ad89bf9159b95897c2122c73faf84ac9a6088e97f6b26a76", "random:2"},
            {"32f03d6f62aac63fd11309b7fbb9b79b52aa798357d11498a0f13a8f2011144d", "all:1"},
            {"d7a387e394c529edb401fe7ed8b974999d28c8bd50b373bec50ff7630322ef85", "olympus+hades+nyx:1"},
            {"2cab30e4622229e1c4420a73a6a095435d15f0c62b93de21daaed2d197e816c5", "random:2"},
            {"ecbc07d69d63c9263eb23dd07172dbbde417cba41a814b00b594e0445360d209", "titan:1"},
            {"82a3177ffd8d578bc5e5aaaffba37fa2cb816498067f75843657cb627df0787f", "tempest:1"},
            {"b08de2b96e8a5934a8b829e97b36e7995797d893e2e852c43afd7179371dce20", "titan:1"},
            {"b9f7e09fc2bb460af8367bba7da52ba56802738e6a5276a73d86216e4c272f55", "olympus:1"},
            {"51be361315df6937272160d3ed8f473fe2fd06290a4e5db2adcbb5ecccd5876f", "poseidon:1"},
            {"0c3ec03345ed5e3927c3c0ad8255c05a30bae1c1d74808b550d59737161cd7d4", "random:2"},
            {"bab6d36ab2224708f62943029baebb412926df8ae129f886d381aae2c8203046", "random:1"},
            {"e9b554c13172483b2b3b0f5abe6a02465369445ed3acb55d85ab1a4ca8d8a911", "random:1"},
            {"812b4394a2617d55d345c1246f02838bb167e0a50e96b4650adff24b32faad35", "all:1"},
            {"6e707de8627b9c0f219733915225a2dd3891fa0ab0cbd9210db29406a65e771d", "titan:1"},
            {"5178db88191b1960eeefac79506231fa8310045d356050e927292c3d5d65e45a", "random:1"},
            {"c8fc6a15639b6a1692863e908761c260870ba9fb57f6f98bb21195ded2c76792", "tempest:1"},
            {"6e79d51b4ed0d67bfc4e937c1b514378bff8db2b049f82e116c511a508990a0f", "hades:1"},
            {"6c6988cd02a53579063ff8dff8b0355a5905d998bbef3651c89dc6778c2a3b48", "random:2"},
            {"aa85b10a979b95284d28d0f3c00d6d5f5aa9f2383343cf455adcdb8870dfb32e", "tempest+poseidon+titan:1"},
            {"2512557a03f8d0049716dc7c03ac3ea758b9ef982054dabcb00a1a1ee8345666", "tempest+poseidon+titan:1"},
            {"37ac89463090f6b3a306052040e693dcb026f6b6f8c0fb2dfee4c939610c3c46", "titan:1"},
            {"41a027f60c56967523d6a82fe317a3426750176dfd71b9dcf307e6ce06644118", "all:1"},
            {"f589f7b474ead03d158fd461776c8cd4bb1f01a2d70c7fa87f82353fbdcbfad0", "hades:1"},
            {"7b0647fa45ff2245e8288dd7971a803b53d41fbb3b88d409dd66db658b4e2214", "olympus+hades+nyx:1"},
            {"3d2f01653d0d6e1a3701dc785f11fe4c6d137d76a2067c65f9f1f44bc0a36fe1", "random:1"},
            {"fbd7dfee0b6e177f3e1e0d003561c03409598be46c7461030ff9ddbb734ce960", "random:1"},
            {"062ef82340f669641e0e9679553138aa826af16c98b78a9eb5910138425f9ebe", "random:1"},
            {"53a2b1ce1b8396e1cb250909757961c8f96a14675cc35e038bda7833031265d6", "random:1"},
            {"0235b64274381e2db17183222d8b2f078a4773dc620bebdda8a44929317e49eb", "random:1"},
            {"57620791751a81479e1aa2bf82e6928a6306ab41b0c2954f48eaecb21714c3b7", "random:1"},
            {"125a7f726c70b79de32b12ec21925704aeab070ebf33cfd2bc3f898b911867af", "poseidon:1"},
            {"f4018eb091a6d17c144b59c5731a9733b0df73b0bd9c1699cd8f9d6c08b1eed3", "tempest:1"},
            {"268ee580cc28aa6bf2fec580244eabfbf4e9a9774b0af6c012d07815e932f0be", "random:1"},
            {"d4bf5b0406a2430c679a29956f278d2cd071624b37445fde4da7c23f72152c0b", "tempest+poseidon+titan:1"},
            {"b49ae50f36166fe0ee2aef47c70694947c621ff1f660d5a07e74ef4d1c4ec362", "random:2"},
            {"4388ae535de074480c200265dfdfdcba25c3cbd6db0ab8d390c1078592b1abce", "random:1"},
            {"ac925f0dc3c8ae9bbff52cc157673f7ced0f2714d1beb6048efceff3e894c24b", "random:1"},
            {"61c98af1982a4e4c1dc0ccb2fc3a90b6ef25cf0ca85c74595ac0975f6c3700a0", "poseidon:1"},
            {"9d7b9ceb65db8fb73f1706fc6376dd32e1e66ba02d11c913f55752e1c7459901", "hades:1"},
            {"5ffebd5965fad2924857a4b063877f74ee5cfa92e0d90f6182e9622d60ef6e3b", "titan:1"},
            {"c4c9935ff2396bf6193f39e909e4714b0ef6b522bd83a89702bbb8591896f405", "random:1"},
            {"a803bf43c5dc7938f00fa293d2510b2b19c98e6f1195dcaa92df9444d664b0f2", "random:1"},
            {"889e94d295b70d3fdb269efebfc414b98d97011ca849c2ec1da6148cd5e02519", "tempest+poseidon+titan:1"},
            {"76f0213bb33e6a61b6b40fe65c29216479752951b1886346a02458cbfe198e72", "random:1"},
            {"c0b3d3eed12318dc56be052bdab6f8396debdb914a9991395b0a75d1db8dffdb", "nyx:1"},
            {"f4851ec7fab75416cc54f4130b5ec9319f7cd7ac01b4a21fc31ef1a3817c215d", "olympus:1"},
            {"5743782dd7c8818463eb6fddd9496272c01d668fddf220536b972a22afb46f30", "olympus:1"},
            {"61be0d298e9d65d1867b067c566a38d934df0ab3183a94be1cb9ccf0ebf70fbc", "random:2"},
            {"9bbc9a418b04bd2640cf3ea04a67b9f744560ab6f95dbadee17ba72562c7ee63", "random:2"},
            {"e2070b7e2d9aad06e776ef91b5aa2380618d30989ccdb2b36467bfc8ec9eae8c", "olympus+hades+nyx:1"},
            {"0a1c4ade22ffaa28554c719fe895a142b9274f03fca036210a83af8407880bb8", "random:2"},
            {"f8731889364f4cbefe12ec8b6afa06fd9e633d6285b61be1649ad0aa8a6072a8", "poseidon:1"},
            {"447183b7ef6eb20ad93773c2878852a2455c2d37bfc058f8a50a6b757a9f251d", "tempest:1"},
            {"b8433b177ccd7884e9dc556bd5934154f9a7e9f92fd4deee02101ae34bb74b95", "random:1"},
            {"fc18260f2ca0adb6924ba15d950676d9439d15aefedf93a21120ffd2dbc450a0", "poseidon:1"},
            {"02ff6bb3ec1de9d8cba399f9cacdb251b171e0cd6539dd82ca38444aaac66e40", "random:2"},
            {"e56793ba84251495369ea492c751b72f22695bd92aababa5d02b2d9eb0e8a916", "random:2"},
            {"5866485c4a22e56714d8a533e068c0cb53264c708dff79a2ae9cce2259fe5475", "olympus+hades+nyx:1"},
            {"15cbf948ae9ead37dc247c1b84564eafdc3678e3a1e959bbc022f67e5deca034", "random:2"},
            {"ae2a7480aec334083bc846174c972239f00298eca0516deb2eca7f31392ce42d", "random:1"},
            {"4f99554d2430deee7f2d86024772853953769bfd5ca88b3ad2bab690ed4cf8d8", "random:2"},
            {"369a4c35463bae936a07820e4332c9d870e078548a65e3aeedabddce3e803fe2", "random:1"},
            {"26375a21c6b4cc8ed80c99abdb1e32d71f7069695cc723102df28eb46eb8154e", "hades:1"},
            {"9797f57c5914d7c68528cbf249715624e6d084e4cf58cc339609837af430c1e9", "titan:1"},
            {"50626a80b4cd6c7d333b232a2f9d26a5fe8e2a02d545ff6a66fe22b6025531e8", "random:2"},
            {"3bbeb6aed01054bb33bfc9c1f2cbf55f83d19931486d6a09c55b6c9f560bc7d7", "nyx:1"},
            {"071e537a87fcdc62b20f5163e819d7a3be4d4a25fc3fa27c30393234d906c7a5", "poseidon:1"},
            {"6f96d66086381e69a10013fc8e952f2f90d00d249275e587e8364661a23fcd45", "random:2"},
            {"ddecf4c28e74fce064f920a868481693b63cf32ce47b1836e2b278a0a6cbc628", "all:1"},
            {"1bd47647dc146d73939a77d6a6bdf8e8a6aa521c4466901173c7013dacb67885", "random:1"},
            {"ebe364a5f37fdfb45324e9f506149ded6c89fe22bbd04967577fbe26aa862c3b", "random:1"},
            {"9d19e38382a0bcd4e5a73cd1df9b312af5092b5a5c476610a3df72af3cad42db", "olympus:1"},
            {"0ad9e3d74248a3d86f2a587deedda0578eca3d824b0a35e49215668760fe74bf", "olympus:1"},
            {"f316b710a79fb259b630b07f3962e8886bfde3c0157efa833b45982883682288", "random:2"},
            {"39f1f8338b2771cf23a86281d0459e935f02a2bbd79579339b191cd0d019c929", "tempest:1"},
            {"c06cf16abc44bc2572a9843f1fac63f7576429640a2da6fdce6daf25f64b44f8", "random:1"},
            {"70c28ae017d42d133f50aa3af4c7e719c8217afbc90bad279cf084cedba4c5da", "nyx:1"},
            {"87e643bef390b2e22699c64b46704b47afda9f74332e825ae1175a126eaeff90", "random:1"},
            {"e7d8b97be41e5a0b530f72524dd1fdd076af4e39ec30a0c8c4476d53cb87c8f7", "hades:1"},
            {"92b5fff485a7cbfebaa21ac65778755b4903857eed3fc6dadc2d369b73a1176c", "olympus:1"},
            {"57025d0b4866acc2f3ad118e691d40c5edd7453fa0ef901c54c83da3bcb6a9f0", "random:1"},
            {"ea7527bd1596c2c0781cd165145a7dd154b12df53c75c7012e0b786c2e634c19", "random:2"},
            {"1ebc6144f8cc31d71e8db2768b9579a03b3dcd2c5f00201a06f7d835fdc735cf", "random:2"},
            {"c13a1a1181c4d1513803f5924d0dcd842d1739b51533fa0593a9db6f37981a7b", "random:2"},
            {"9ed144db3582f1c520cd2bc575012689888a2a2da9e000e7c6053df357c6cb0d", "random:1"},
            {"aad3b304b939b331cda4774d8a3db6dac1a2722ff2be1468e10fbc971b596709", "random:1"},
            {"b6c160fc1465de2a550451ba4bfa73e980a18934751abc58d9d4e130d6ee5582", "all:1"},
            {"f333eb9f83b22c4b5324c933079b2b0bcb2460fe2b3b8172a9c2961f838dc60f", "random:1"},
            {"7fd791c978794e595f482ffc1dd7b037747b778ec350bc080867183f6ad73a22", "random:2"},
            {"cc746be88c3c865cdcfcc850658f9298dd5168cbb9e49289f1ae0abef20fad08", "random:1"},
            {"83da381e9066f2a2b9e6bfb41a1a4018f63b9eefad3ac56a2853b6eafe7147a8", "random:1"},
            {"b40888e56be8ded27040815d195ff1b1b249e06f497fdd931ed60c9e3c2594f3", "tempest:1"},
            {"b94e20d5c7c9967b39c897c578d72a0694f3e9e7fb8311e6aa80b4e2fa0391d5", "hades:1"},
            {"9c603e452f33d4fe90f0f019c0db8c3c01c23541d66650eeea77a444054a83b2", "nyx:1"},
            {"96d2399ddfd16d60d3bd8b022e7528c052ddf7b070f0772f8f09d4681477ae55", "nyx:1"},
            {"1817626fe56470c32b02fc51caab3996b6a8915c1b6cc5349d5a55af178a731b", "random:1"},
            {"6dea54b17c2ad5be3ea52bad89f0d7accb1311c257393758c443f5d347fa684e", "all:1"},
            {"067d5ff5449328b3aa1828c58d970b9ada777f89bcb6272e4a51f4b510836203", "random:1"},
            {"0be4d3a13396c9656696819dcd4ed24e0ef43113aae508b6ca0debe6c733e495", "random:2"},
            // admin: keys to open every crate n times plus tokens, works any number of times ("*" = reusable)
            {"3aa6ba0b339531d4dd7de2311eda45c3631d697b062999f7bec01af6c27b4e18", "admin:6:*"},
            // second batch: keys and tokens ("keys:n", "tokens:n"), and new admin codes
            {"cf5ab8e15aefece1ae9dc9736e643bc392b9962cf84a422448a99f1ba47e2fa0", "keys:9"},
            {"898f7b26edba8742ad798d152a36217f63d945df01313ce57df60dfba1c22131", "keys:9"},
            {"63051a7c89e4974360b8212cf743a8c74cb6a113d9a1bf22b5c491e5866d3943", "keys:9"},
            {"521f82e92c8b595e31346458e21046e310cc96c9ee95e0ae9e8b4684fbf6b651", "keys:9"},
            {"1a293bd0dd23b93df1e1e200f78256151a15462f5e3bd58a7ab31b058647d5f1", "keys:9"},
            {"6a89a60c9f125baff9c2a41f9472f4d3e2a08be58a794994565b20e51ce78b35", "keys:9"},
            {"c71fa5a741e48dd9f4e3fede160a980e1260a14cce0038490525d638cae7290a", "keys:9"},
            {"52fc31bca08583c48b564bda425522c93451710d3bd0ce8efc16d0ad29c0ad7a", "keys:9"},
            {"e82b6b99e4d3fa889ade3c8242570dc2ffa36657c4bdded543f6d931ab03067a", "keys:9"},
            {"96253437bfd8827b63b71f01d8e946866df2d3d0aad85f1b6f0d47e89a228ea9", "keys:9"},
            {"69bacdf48d8a3f9b91955ba5b6f7b773984d2b89853d231175552bd895f5de20", "keys:9"},
            {"9e566b2124cefe1b30e0dc1d274622196522474807b0f1353f32fda24221f87c", "keys:9"},
            {"6a35662f3eef1cb5f6567eadf360e524b0a84cba0741121d58c950cd2f3cb4d8", "keys:9"},
            {"eace972b878b60407bb2e669f617c28b07d9568e8313f49a77ba1e98ec6dc90b", "keys:9"},
            {"64b07a2c011577da2edaa75b2ee7415fde16491eda53d14ffe44cf860bcb32c4", "keys:9"},
            {"0662c77d02b1e338f119a6987d22480218450fede2edfe7d2acc6cbf45697506", "keys:9"},
            {"6a9a7cc9dd2c6c1afd08b4ff658e404f2bedfec0cd936e9ddbb0c41df1169f4d", "keys:9"},
            {"d9709693553ea043ae3f1e47f32fe6bfc7b79b5903a216bba72ab85ad7929e51", "keys:9"},
            {"19718821f720656d52c378ca62f9885b4fea803aa80dfb811a2db6fa71b8706d", "keys:9"},
            {"6a10f0e61a87b8ed718ebe46271382c71ff11b027f0d8800bdd3e4ab7a6722d0", "keys:9"},
            {"8f386dc262f8f9f9312547a7d50d912cb10a85e8833f0c934ff4b892eede01d8", "keys:9"},
            {"27e3d89a42ed64b110e895ca3fe90e78c54df89d69180ab4353118b69ab62a02", "keys:9"},
            {"6e9b58759dd754cd4625cc820b33b28535012808a93474bd3920ee796561e6c2", "keys:9"},
            {"9e5e00d80da6f375f6e7ed0bad9f689394692dda8fe9a0d6ee13d528d3a9df98", "keys:9"},
            {"1f783118d4b40fab9d1ba179990995b4785ab90544aa9596b616b924c70f6875", "keys:9"},
            {"b16370f65bb0bae3d202fe96e9387d759df649fffb782849dea6bd4aac844dea", "keys:9"},
            {"4942cf23e83dfc7909921c49ee3d60931aae3c53c6fb4c8fcb1e69ff93aef6fd", "keys:9"},
            {"857de2cc58ef8c0672050598c21861d587199a5e043406eea10c68f6c71dd59b", "keys:9"},
            {"8df6cf234943c37b5114bb5f389c57c921edd3c9c1a9e605d71f37b11fc93d70", "keys:9"},
            {"860b6ef2e9e3c9cdcca802ad02535363e629a93e4a0aa8406b1da036f316291b", "keys:9"},
            {"3619b271c75c7b0d5b92c2753b93b254b0640d0a60bc67575cf199e1a416d6c1", "keys:9"},
            {"356d13e6bb292604f709a3f25d76ae5ba855acde5eea6f805bb974f13c996160", "keys:9"},
            {"2f76c2be5302b3d210e710bd8cfb7f1fa2f73ecb3ddf3f93b30ce25a2f022dd0", "keys:9"},
            {"cb68a80d78510248af7728d2c7c853c8c2f6be8a6adea9d5e6a32e153a27ee3e", "keys:9"},
            {"20d8c22794a7d53be52f41f4b17ff41c9d2ca35ceae60b4a0e8844adb10eb069", "keys:9"},
            {"b3dd5b264d946b34d19c2b41e283d3ef4d9cf6bfd8398868f9b0a1a2c4852e7d", "keys:9"},
            {"645e17cd0a0ddc1a356b8deab78fd841c96f50ae4180d5aa0d9806295bddf37f", "keys:9"},
            {"f6e97f9c0d8272bcd96b9ca5d98eadfbea3a6314b4fa4970a470b4b923208323", "keys:9"},
            {"547f632af3524892e6fbb2dfe82ee0bbc51facc47592ba63fb2b86fa483760d9", "keys:9"},
            {"e3de66e7e6877357e50ac147b72852b65d828e32bca2ba6a75ac1920412e0b89", "keys:9"},
            {"aaf2508dfb61ab819ffe1b914b6e72c04380cc2862c093c36bf1e93c1695e76c", "keys:15"},
            {"475004753b519ad7419b39ac45926c641da03007801df0a6bdd905df4df20759", "keys:15"},
            {"0ea6140f4f9e7bb2fb004e5a1d2689a6bbd2992492f608e7150627e00d9fe29b", "keys:15"},
            {"973e2aec6c50a2d0e6d309a4599245bb3d1240d072c9e6421b01f75dc7d7b43f", "keys:15"},
            {"35ffc8ca67eca8888147205b1d7168eb1aecba364186e4add26d693de7d7e5be", "keys:15"},
            {"290625ec8adaaed509efe40b2843ff42e621a6f2483471447fb238e618f336dd", "keys:15"},
            {"17504e60bdd9bece208d43816a0a4090d25f6f7fa8331c6b340e598cabdab9f8", "keys:15"},
            {"3e76f9441f003b7fc0c4b4dff4839043016ce2087c70887c3a98b638fd5912c7", "keys:15"},
            {"b1434f7cb8eb634305d0bbb75b25dae83f23490dab8501d49201ab18bb1310c8", "keys:15"},
            {"27aa35e197d1eb8721557363a6c335dfbc8d0dacd61c8ff0cbf6d5db4804df49", "keys:15"},
            {"5580ba7e2e5014478986f1ebfe2c167b114dcc8b2650e21a6b22d92260109814", "keys:15"},
            {"8d95aea276887151605cd8dc4f8dfb66f7425b1f196ce1313b7ca663a889ce78", "keys:15"},
            {"8b95037c459f724a0e488c8bab1e71fe73e31354cc22d708f3fd5b2e65244c6b", "keys:15"},
            {"7153e9e47fcac823c6545281e3e9ca3057169476e4b9d7df5df4824744d255ba", "keys:15"},
            {"74be38b17d26eece90e8e019bb950175885a3f786a6ff8e003a3ffffc12a1591", "keys:15"},
            {"a88c504fa3b66bc2a137c7e4d1b9710e8b40dc105a3d07a8a7e7e3545f38e3e3", "keys:15"},
            {"77df19efd670d4bd61114af9e713955015a1d92d81daa406fff5c2502b6c1553", "keys:15"},
            {"982f185ed88dcb047ddbd48fdc8db73cb51808ecfe050ad382cf9e91f9328c50", "keys:15"},
            {"98198088bb6196883dd8137d9ae632b847f563f5d98001f2bd4fcc517a493a49", "keys:15"},
            {"36a1d1008e4a745b0f81e56200afe78640f1ac9bb70cfc6fae8d312d2c74aa65", "keys:15"},
            {"88b4b6cc3a5b3a5db197de4253c70c19a594dbae9b71669d2245a39d3f457000", "keys:15"},
            {"069aacd1fc75a9360c8ecb7f776a799601450d6706e6f92c17ab8ee08ea707fe", "keys:15"},
            {"0647cf317223cf5f72dadbe779637cb7635ad8b1c8fe78173e892976a0c38d12", "keys:15"},
            {"559edf8382cd07d63a6a7473b4e0d28952aafecfea997f1b656949934aa4c3de", "keys:15"},
            {"938fc30f86aefabdf89971f386fd4ce202babe00940b291c9674ee3cab92e283", "keys:15"},
            {"382d586e304598402fc3573041cc31f51df92d09c726468566ab243d1b5ce10e", "tokens:15"},
            {"c4232e291e8d7078f7d9095eb87d77f094760e830a6906970d1893df1c3a9ecf", "tokens:15"},
            {"f981156dceec36176c1b14733b57c9ca6c5c479e30f8f8c2d526c8aacd29009f", "tokens:15"},
            {"f865f3a652af7cdc551d92a115b6d7e10731c9c397c36a896821160415c38b4d", "tokens:15"},
            {"50e3992df9925dd62ccbac6ff41659740a1b4f5c8387fc7accb82915bfaae4df", "tokens:15"},
            {"3887a0857c2a436bab1093c903c7cd01cd9173fa9ceed7db9aaed213ded20a5d", "tokens:15"},
            {"da61fc7f7cdf75c17a30328ee45ae0a7a75e896a3e982ae7c97a8f93b9e05018", "tokens:15"},
            {"363865a90286250d901725ed72d014d7b1d81363d847f28554f104face0f5b87", "tokens:15"},
            {"c506e8fe200dfae765f2a1bfbc8f417f3c2b7a5ab782c8b12bca0088a99976e0", "tokens:15"},
            {"5d2377ce39e98299ba0039b2dd56e1260e3766c6c029e6989b7d5c5677e25a03", "tokens:15"},
            {"42cf9bbc7d74372fed084ff5519f01e9200b503fa8c93854a81f21507d1907db", "tokens:15"},
            {"9061ab8dae3e02a4d73f302e7472e16455cba5c2bdbaae2ca603a66ab05a2866", "tokens:15"},
            {"9fbd5bc2d1c2a5d7fb8bcea8e56a94877e618aa17284cda7312d77db54bfe238", "tokens:15"},
            {"174d57faddabee3528b1d6c6ff92a62d7eca26592024ef4f27156efbc04c61e0", "tokens:15"},
            {"577607f2b8b72bc3eafa77d205583fc155764776f28616e86774c499876cbca4", "tokens:15"},
            {"17e3fd20493cfbac49495bd80307a63e780a9a6c07e52782cc587e3bed8669e4", "tokens:15"},
            {"fc34bf4a5bb527a4495469cb0b6385b37f568ea11624ae4935c99d530104a7d6", "tokens:15"},
            {"f72b8398b2bd2c505ffc5a92cfd5a6f6fd04dae680df96ce3547dc4e6ef9cd02", "tokens:15"},
            {"b622bce6a324dee6d48b5ab3a9292da67bc0d903363298c5aa3212cc3e560fc0", "tokens:15"},
            {"f482a299d0e2705164e8abd62ca12b62a3af3f85734fea21b4590f59ed0f6917", "tokens:15"},
            {"689e88546151b229452324a067736a6c779e35915f04adeda5460f94f3cb885a", "tokens:30"},
            {"7809b58bb64e95f4eb281af9e93652d9738ba97270f12690d02837392fe79020", "tokens:30"},
            {"f5a316b55637cfde6b147c068233fbe6ff35f41dcdbe9b71b3da781115f03e3c", "tokens:30"},
            {"078529f0700c29216d0eb4b3dccda93a27adc367ed5ee4d9d8db487767b64f4a", "tokens:30"},
            {"c77e3261baef5afc0a1b94ddeb5146cd5fc8c2b3c65f530980242f70ac18edf2", "tokens:30"},
            {"937524e410260f86bab1cb977f3a80968c459871154141288f7c75a322887507", "tokens:30"},
            {"cf2ec33d22af61aca371f9349fcef54ba346e02756e343a6b5df808edc55893b", "tokens:30"},
            {"1bccfc46579bb4faa89dc4ede5abb72cd3283237edca40ca1ab7bb40667f817d", "tokens:30"},
            {"01188f52e540beb64061f5fcb58082d4b60201c0ff4352cab3c5b9711269134e", "tokens:30"},
            {"fa1dd444ac8423d0c43569207ecff66a06e944c5942adae41d7d3cc5e86086e8", "tokens:30"},
            {"0c211541af3084ba50747582a36cffac0239e80daab9d599b01147bd22a748fc", "tokens:30"},
            {"9adc191eadd57aa234025769499cb0be97b2b8df1a39aef164ab09fb3b634a2e", "tokens:30"},
            {"dd1dd2a2e350c76370a42cb77fa12a28890a454846e8d6a14ff475950bdddea1", "tokens:30"},
            {"3345674c27ad7b2ee5dcbbd3eb7f8117022ed8dce5fd6c8cbd2e0fc8058251c7", "tokens:30"},
            {"ae33d53da9c15d058fcce0dfc911066e75c2cf761b127ecbbccc373bb78b5b7b", "tokens:30"},
            {"2abcf846a8cf9425e1576aa2a10ae5b9bda6fdbbd27f6d890dd350e0f027bfae", "keys:30"},
            {"0859ed54c9342f7ec4c505bacbd917c6bd6a45a2f5b6fc122af106c4b06313ea", "keys:30"},
            {"72ada1b0b6bd18cbf6886e7c355e751099282c7b1b7b705d362a4b3b77051c4c", "keys:30"},
            {"0317e75f9f4eb41bafb593f15e68cbdd24bfdc71bb28034cf580258a25505a6b", "keys:30"},
            {"4420b064219d5583e1eb40f18da765027f803bd5de77d54b3a39ee9d38625202", "keys:30"},
            {"359b017603d0bb9300aeb0593325506e1694a062366d0cfcb077698e6b7dbdac", "keys:30"},
            {"a496e44d0eeeb2578b41d359f53e71c8c495dce72eeb6b6019e7820a656e2d3b", "keys:30"},
            {"fc8f1ce1fdb85a52d3dd1517cceca15fec3c41e02501b1194e3b97fdf4b7b00b", "keys:30"},
            {"1fe3febb40412056ec1a58b79268505f5e72b68d9a93c426fd5be263d2897fd0", "keys:30"},
            {"0bf51da5d46fe2b0f9523368eb60c88eef85d06adef73d880b7d13e44f848870", "keys:30"},
            {"25de490701ba1725391fdcccc6963635b8358626c04e7d42912ed46a4012e5b6", "keys:60"},
            {"d983a0409d10e54180336bcdef507848496637269554156eeb1c0af5d3a899d5", "keys:60"},
            {"3d3ffafdfbb4b47b850ff2972da1433a5a1c0aed8b875416dc76c84239863685", "keys:60"},
            {"842825b46e387467da9b281fe1d67a7d03c30fe6bb956da0065a9d9ee8b86bd2", "keys:60"},
            {"3629cf1f07069456fab4a964f7180b6d35889c7741e7647dc038cd2ca41ddac6", "keys:60"},
            {"937127fb35121883e018232fb42429309fdef5989555ddb02a97141b748a1e89", "tokens:100"},
            {"f13df2ac4ef8e271184eebfbf52759e0edd372f4db60c191b9991579102af258", "tokens:100"},
            {"b5e148cfa4df40dbd60ee91388d9411e7119bb3efdf89f7fb72cd11cc0a15c37", "tokens:100"},
            {"faaacaf631b7811ab810cc77c0f260043bbce3dcbeab5bc02941dfe1b5032b3f", "tokens:100"},
            {"44c6347a3965b3c380d27afafbbdd20f625f53eb0dc2e3769095d4b9bf774f95", "tokens:100"},
            {"aed059fc7b49f8400076be394370eb54356369200e9db96c81618a95b786d8a0", "admin:10:*"},
            {"f73a74824b9c909716231b3ff8af872ba7944e6b1ddde734b580a4b7e8ca1b09", "admin:10:*"},
            {"a3d115994fa1d2808275c8e63787d3e7740f695533722a44390c3eb4d3bdecb7", "admin:25:*"}
    };
    /** Most crates opened at the same time. */
    public static final int MAX_MULTI = 3;
    /** Keys are capped so a reusable code can never overflow the count. */
    public static final int MAX_KEYS = 9999;
    private static final int MAX_FAILS = 5;
    private static final long LOCK_MS = 30_000L;
    private static int fails;
    private static long lockedUntil;

    // ------------------------------------------------------------------ state
    private static int tokens;
    private static int keys;
    private static final Map<String, Integer> held = new LinkedHashMap<>();
    private static final Set<String> owned = new LinkedHashSet<>();
    private static final Set<String> redeemed = new LinkedHashSet<>();
    private static long activeMs;
    private static long dropMs;
    private static long earned;
    private static int opened;
    // lifetime stats
    private static int dupes;
    private static long refunded;
    private static int keysBought;
    private static final Map<String, Integer> openedBy = new LinkedHashMap<>();
    /** Opens in a row without a Divine or better knife; the PITY-th one is guaranteed to be Divine or better. */
    private static int dry;
    public static final int PITY = 25;
    private static boolean quick;
    /** Keep opening on its own after each reveal, while keys last. */
    private static boolean auto;
    /** How many crates one click opens (1 to MAX_MULTI). */
    private static int multi = 1;
    private static int streak;
    private static int bestStreak;
    private static long lastDay = -1;     // local epoch day the streak was last paid
    private static long countDay = -1;    // the day dayActiveMs is counting for
    private static long dayActiveMs;
    /** Most recent drops first: knife id, crate id, time, duplicate. */
    private static final List<Drop> history = new ArrayList<>();
    private static final int HISTORY_MAX = 50;
    private static boolean loaded;
    private static boolean dirty;
    private static long lastSave;
    private static final SecureRandom RNG = new SecureRandom();

    // activity tracking
    private static double lx, ly, lz;
    private static float lyaw, lpitch;
    private static long lastActive;
    private static long lastTick;

    public static int tokens() {
        load();
        return tokens;
    }

    public static int keys() {
        load();
        return keys;
    }

    public static int held(String crateId) {
        load();
        return held.getOrDefault(crateId, 0);
    }

    /** How many crates the keys held can open right now (the main menu shows this). */
    public static int heldTotal() {
        load();
        return keys / KEYS_PER_OPEN;
    }

    /** How many crates can be opened at the same time right now, up to {@link #MAX_MULTI}. */
    public static int canOpenCount() {
        return Math.min(MAX_MULTI, keys() / KEYS_PER_OPEN);
    }

    public static int duplicates() {
        load();
        return dupes;
    }

    public static long refunded() {
        load();
        return refunded;
    }

    public static long earned() {
        load();
        return earned;
    }

    public static int keysBought() {
        load();
        return keysBought;
    }

    /** How often a crate has been opened. */
    public static int openedOf(String crateId) {
        load();
        return openedBy.getOrDefault(crateId, 0);
    }

    /** How many different knives have come out of crates (the starter not counted). */
    public static int uniqueFound() {
        return ownedCount() - 1;
    }

    public static int opened() {
        load();
        return opened;
    }

    public static boolean owns(String knifeId) {
        load();
        return STARTER.equals(knifeId) || owned.contains(knifeId);
    }

    public static int ownedCount() {
        load();
        int n = 1;
        for (String s : owned) if (!STARTER.equals(s)) n++;
        return n;
    }

    /** The knife id of an HD knife index (the order of knives/hd/tactical.json). */
    public static String idOf(int hd) {
        KnifeHD.Model m = KnifeHD.model(hd);
        return m == null ? "" : m.id;
    }

    private static Map<String, Integer> HD;

    public static int hdOf(String id) {
        if (HD == null) {
            Map<String, Integer> m = new HashMap<>();
            for (int i = 0; ; i++) {
                KnifeHD.Model km = KnifeHD.model(i);
                if (km == null) break;
                m.put(km.id, i);
            }
            if (m.isEmpty()) return -1;
            HD = m;
        }
        Integer i = HD.get(id);
        return i == null ? -1 : i;
    }

    public static boolean ownsHd(int hd) {
        return owns(idOf(hd));
    }

    public static int starterHd() {
        int i = hdOf(STARTER);
        return Math.max(0, i);
    }

    /** Milliseconds of active play left before the next token. */
    public static long nextTokenIn() {
        load();
        return Math.max(0, TOKEN_MS - activeMs);
    }

    public static float tokenProgress() {
        load();
        return Math.min(1f, activeMs / (float) TOKEN_MS);
    }

    public static long nextDropIn() {
        load();
        return Math.max(0, DROP_MS - dropMs);
    }

    public static boolean activeNow() {
        return System.currentTimeMillis() - lastActive < IDLE_MS;
    }

    static void markDirty() {
        dirty = true;
    }

    /** Tokens from elsewhere (daily quests); counted as earned. */
    static void addTokens(int n) {
        load();
        tokens += n;
        earned += n;
        dirty = true;
    }

    // ------------------------------------------------------------------ daily streak
    private static long today() {
        return java.time.LocalDate.now().toEpochDay();
    }

    public static int streakReward(int day) {
        return STREAK_REWARD[Math.max(0, Math.min(STREAK_REWARD.length, day) - 1)];
    }

    /** The streak as it stands now: 0 once a whole day was missed. */
    public static int streak() {
        load();
        return lastDay >= today() - 1 ? streak : 0;
    }

    public static int bestStreak() {
        load();
        return bestStreak;
    }

    public static boolean streakClaimedToday() {
        load();
        return lastDay == today();
    }

    /** The day number today's reward will be for. */
    public static int streakNextDay() {
        int s = streak();
        return streakClaimedToday() ? s : s + 1;
    }

    public static long streakPlayLeft() {
        load();
        return countDay == today() ? Math.max(0, STREAK_PLAY_MS - dayActiveMs) : STREAK_PLAY_MS;
    }

    private static void streakTick(long dt) {
        long d = today();
        if (countDay != d) {
            countDay = d;
            dayActiveMs = 0;
        }
        dayActiveMs += dt;
        if (lastDay >= d || dayActiveMs < STREAK_PLAY_MS) return;   // paid already, or the clock went backwards
        streak = lastDay == d - 1 ? streak + 1 : 1;
        bestStreak = Math.max(bestStreak, streak);
        lastDay = d;
        int add = streakReward(streak);
        tokens += add;
        earned += add;
        dirty = true;
        save(true);
        Zeus.toast("Zeus Crates: day " + streak + " streak! +" + add + " tokens" + (streak < STREAK_REWARD.length ? " (tomorrow +" + streakReward(streak + 1) + ")" : ""), true);
    }

    // ------------------------------------------------------------------ actions
    public static boolean buyKey() {
        return buyKeys(1) == 1;
    }

    /** Buys up to n keys with tokens; returns how many were bought. */
    public static int buyKeys(int n) {
        load();
        int can = Math.min(n, Math.min(tokens / KEY_PRICE, MAX_KEYS - keys));
        if (can <= 0) return 0;
        tokens -= can * KEY_PRICE;
        keys += can;
        keysBought += can;
        save(true);
        return can;
    }

    /** True when there are enough keys to open a crate (any crate opens with the same keys). */
    public static boolean canOpen(Crate c) {
        return keys() >= KEYS_PER_OPEN;
    }

    public record Result(Item item, Crate crate, boolean duplicate, int refund) {
    }

    public record Drop(String knife, String crate, long at, boolean duplicate) {
    }

    public static List<Drop> history() {
        load();
        return java.util.Collections.unmodifiableList(history);
    }

    public static boolean quick() {
        load();
        return quick;
    }

    public static void setQuick(boolean on) {
        load();
        quick = on;
        save(true);
    }

    public static boolean auto() {
        load();
        return auto;
    }

    public static void setAuto(boolean on) {
        load();
        auto = on;
        save(true);
    }

    public static int multi() {
        load();
        return multi;
    }

    public static void setMulti(int n) {
        load();
        multi = Math.max(1, Math.min(MAX_MULTI, n));
        save(true);
    }

    /** How many of a crate's knives are owned. */
    public static int collected(Crate c) {
        int n = 0;
        for (Item it : c.items) if (owns(it.id)) n++;
        return n;
    }

    /** Count of drops per rarity, for the history stats. */
    public static int[] dropsByRarity() {
        load();
        int[] n = new int[Rarity.values().length];
        for (Drop d : history) n[rarityOf(d.knife).ordinal()]++;
        return n;
    }

    /** Rolls a knife from a crate without spending anything: the reel is built from these. */
    public static Item roll(Crate c) {
        int total = 0;
        Map<Rarity, List<Item>> by = new HashMap<>();
        for (Item it : c.items) by.computeIfAbsent(it.rarity, k -> new ArrayList<>()).add(it);
        for (Rarity r : by.keySet()) total += r.weight;
        int pick = RNG.nextInt(total);
        for (Rarity r : Rarity.values()) {
            List<Item> l = by.get(r);
            if (l == null) continue;
            if (pick < r.weight) return l.get(RNG.nextInt(l.size()));
            pick -= r.weight;
        }
        return c.items.get(0);
    }

    /** A roll among this crate's knives of at least the given rarity, by their usual weights. */
    static Item rollAtLeast(Crate c, Rarity min) {
        int total = 0;
        Map<Rarity, List<Item>> by = new HashMap<>();
        for (Item it : c.items) if (it.rarity.ordinal() >= min.ordinal()) by.computeIfAbsent(it.rarity, k -> new ArrayList<>()).add(it);
        if (by.isEmpty()) return roll(c);
        for (Rarity r : by.keySet()) total += r.weight;
        int pick = RNG.nextInt(total);
        for (Rarity r : Rarity.values()) {
            List<Item> l = by.get(r);
            if (l == null) continue;
            if (pick < r.weight) return l.get(RNG.nextInt(l.size()));
            pick -= r.weight;
        }
        return roll(c);
    }

    /** Opens left until a Divine or better knife is guaranteed (1 = the next one). */
    public static int pityIn() {
        load();
        return Math.max(1, PITY - dry);
    }

    /** Opens one crate with {@link #KEYS_PER_OPEN} keys. Returns null when there are not enough keys. */
    public static Result open(Crate c) {
        Result r = openOne(c);
        if (r != null) save(true);
        return r;
    }

    /** Opens up to n crates of one kind at the same time; as many as the keys allow. */
    public static List<Result> open(Crate c, int n) {
        List<Result> out = new ArrayList<>();
        for (int i = 0; i < Math.min(n, MAX_MULTI); i++) {
            Result r = openOne(c);
            if (r == null) break;
            out.add(r);
        }
        if (!out.isEmpty()) save(true);
        return out;
    }

    private static Result openOne(Crate c) {
        load();
        if (keys < KEYS_PER_OPEN) return null;
        keys -= KEYS_PER_OPEN;
        opened++;
        openedBy.merge(c.id, 1, Integer::sum);
        Item it = dry + 1 >= PITY ? rollAtLeast(c, Rarity.DIVINE) : roll(c);
        dry = it.rarity.ordinal() >= Rarity.DIVINE.ordinal() ? 0 : dry + 1;
        boolean dup = owns(it.id);
        int refund = dup ? it.rarity.refund : 0;
        if (dup) {
            tokens += refund;
            dupes++;
            refunded += refund;
        } else {
            owned.add(it.id);
        }
        history.add(0, new Drop(it.id, c.id, System.currentTimeMillis(), dup));
        while (history.size() > HISTORY_MAX) history.remove(history.size() - 1);
        return new Result(it, c, dup, refund);
    }

    public record Redeem(boolean ok, String message) {
    }

    public static Redeem redeem(String raw) {
        load();
        long now = System.currentTimeMillis();
        if (now < lockedUntil) return new Redeem(false, "Too many wrong codes. Try again in " + ((lockedUntil - now + 999) / 1000) + "s.");
        String code = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "-");
        if (code.isEmpty()) return new Redeem(false, "Type a code first.");
        if (!code.startsWith("ZEUS-") && code.length() == 8) code = "ZEUS-" + code.substring(0, 4) + "-" + code.substring(4);
        String h = sha(SALT + code);
        for (String[] c : CODES) {
            if (!c[0].equals(h)) continue;
            fails = 0;
            String[] parts = c[1].split(":");
            int n = Integer.parseInt(parts[1]);
            boolean reusable = parts.length > 2 && parts[2].equals("*");
            if (!reusable && redeemed.contains(h)) return new Redeem(false, "That code has already been used on this account.");
            if (parts[0].equals("admin")) {
                // n openings of every crate, as keys, plus tokens
                int add = Math.min(n * CRATES.size() * KEYS_PER_OPEN, MAX_KEYS - keys);
                keys += Math.max(0, add);
                tokens += n * 50;
                if (!reusable) redeemed.add(h);
                save(true);
                return new Redeem(true, "Admin: +" + Math.max(0, add) + " keys and +" + n * 50 + " tokens.");
            }
            if (parts[0].equals("tokens")) {
                tokens += n;
                earned += n;
                if (!reusable) redeemed.add(h);
                save(true);
                return new Redeem(true, "Added " + n + " tokens. You now have " + tokens + ".");
            }
            if (parts[0].equals("keys")) {
                int add = Math.min(n, MAX_KEYS - keys);
                if (add <= 0) return new Redeem(false, "You already hold the most keys you can (" + MAX_KEYS + ").");
                keys += add;
                if (!reusable) redeemed.add(h);
                save(true);
                return new Redeem(true, "Added " + add + (add == 1 ? " key" : " keys") + ". You now hold " + keys + ".");
            }
            if (!reusable) redeemed.add(h);
            // codes from before keys opened any crate gave crates: each one is now the keys to open one
            int crates = parts[0].equals("all") ? CRATES.size() : parts[0].equals("random") ? n : parts[0].split("\\+").length * n;
            int add = Math.max(0, Math.min(crates * KEYS_PER_OPEN, MAX_KEYS - keys));
            keys += add;
            save(true);
            return new Redeem(true, "Added " + add + " keys: enough to open " + add / KEYS_PER_OPEN + (add / KEYS_PER_OPEN == 1 ? " crate." : " crates."));
        }
        if (++fails >= MAX_FAILS) {
            fails = 0;
            lockedUntil = now + LOCK_MS;
            return new Redeem(false, "That code is not valid. Too many tries, wait 30s.");
        }
        return new Redeem(false, "That code is not valid.");
    }

    private static String summary(List<String> names) {
        Map<String, Integer> c = new LinkedHashMap<>();
        for (String s : names) c.merge(s, 1, Integer::sum);
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Integer> e : c.entrySet()) {
            if (b.length() > 0) b.append(", ");
            b.append(e.getValue() > 1 ? e.getValue() + "x " : "").append(e.getKey());
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ play time
    /** Called every client tick: counts active play towards tokens and crate drops. */
    public static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        long dt = lastTick == 0 ? 0 : Math.min(1000, now - lastTick);
        lastTick = now;
        if (mc.player == null || mc.level == null || mc.isPaused()) return;
        load();
        var p = mc.player;
        if (Math.abs(p.getX() - lx) > 0.05 || Math.abs(p.getY() - ly) > 0.05 || Math.abs(p.getZ() - lz) > 0.05
                || Math.abs(p.getYRot() - lyaw) > 0.5f || Math.abs(p.getXRot() - lpitch) > 0.5f || p.swinging) {
            lastActive = now;
        }
        lx = p.getX();
        ly = p.getY();
        lz = p.getZ();
        lyaw = p.getYRot();
        lpitch = p.getXRot();
        if (!activeNow()) return;          // standing AFK earns nothing
        activeMs += dt;
        dropMs += dt;
        streakTick(dt);
        Quests.tick(mc, dt);
        if (activeMs >= TOKEN_MS) {
            activeMs -= TOKEN_MS;
            tokens++;
            earned++;
            dirty = true;
            if (tokens == KEY_PRICE * KEYS_PER_OPEN) Zeus.toast("Zeus Crates: " + tokens + " tokens, enough to open a crate", true);
            else Zeus.toast("Zeus Crates: +1 Zeus token (" + tokens + ")", true);
        }
        if (dropMs >= DROP_MS) {
            dropMs -= DROP_MS;
            keys = Math.min(MAX_KEYS, keys + KEYS_PER_OPEN);
            dirty = true;
            Zeus.toast("Zeus Crates: a free crate! +" + KEYS_PER_OPEN + " keys", true);
        }
        if (now - lastSave > 30_000L) save(false);
    }

    // ------------------------------------------------------------------ storage
    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("zeus").resolve("crates.dat");
    }

    private static byte[] secret() {
        // spread out so the key is not one plain string in the jar
        byte[] a = "olympus-forge".getBytes(StandardCharsets.UTF_8);
        byte[] b = new byte[32];
        for (int i = 0; i < b.length; i++) b[i] = (byte) (a[i % a.length] * 31 + i * 17 ^ 0x5A ^ (i * i));
        return b;
    }

    private static String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret(), "HmacSHA256"));
            return hex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "";
        }
    }

    private static String sha(String s) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "";
        }
    }

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private static String hex(byte[] d) {
        char[] out = new char[d.length * 2];
        for (int i = 0; i < d.length; i++) {
            out[i * 2] = HEX[(d[i] >> 4) & 15];
            out[i * 2 + 1] = HEX[d[i] & 15];
        }
        return new String(out);
    }

    private static void starter() {
        tokens = 0;
        keys = 0;
        held.clear();
        owned.clear();
        owned.add(STARTER);
        redeemed.clear();
        activeMs = 0;
        dropMs = 0;
        earned = 0;
        opened = 0;
        dupes = 0;
        refunded = 0;
        keysBought = 0;
        openedBy.clear();
        dry = 0;
        quick = false;
        auto = false;
        multi = 1;
        streak = 0;
        bestStreak = 0;
        lastDay = -1;
        countDay = -1;
        dayActiveMs = 0;
        Quests.reset();
        history.clear();
    }

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        starter();
        Path f = file();
        if (!Files.exists(f)) {
            save(true);
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            String body = root.get("data").getAsString();
            if (!sign(body).equals(root.get("sig").getAsString())) {
                Zeus.LOG.warn("Zeus Crates: crates.dat was edited by hand and has been reset");
                Zeus.toast("Zeus Crates: your crate data was edited outside the game, so it was reset", false);
                save(true);
                return;
            }
            JsonObject d = JsonParser.parseString(body).getAsJsonObject();
            tokens = d.get("tokens").getAsInt();
            keys = Math.max(0, Math.min(MAX_KEYS, d.get("keys").getAsInt()));
            if (!d.has("kv")) keys = Math.min(MAX_KEYS, keys * KEYS_PER_OPEN);   // saved before crates took 3 keys
            activeMs = d.get("active").getAsLong();
            dropMs = d.get("drop").getAsLong();
            earned = d.has("earned") ? d.get("earned").getAsLong() : 0;
            opened = d.has("opened") ? d.get("opened").getAsInt() : 0;
            dry = d.has("dry") ? Math.max(0, Math.min(PITY - 1, d.get("dry").getAsInt())) : 0;
            // crates held from before any crate opened with keys: each becomes the keys to open one
            for (var e : d.getAsJsonObject("crates").entrySet()) keys += Math.max(0, e.getValue().getAsInt()) * KEYS_PER_OPEN;
            keys = Math.min(MAX_KEYS, keys);
            held.clear();
            dupes = d.has("dupes") ? d.get("dupes").getAsInt() : 0;
            refunded = d.has("refunded") ? d.get("refunded").getAsLong() : 0;
            keysBought = d.has("bought") ? d.get("bought").getAsInt() : 0;
            if (d.has("openedBy")) for (var e : d.getAsJsonObject("openedBy").entrySet()) openedBy.put(e.getKey(), e.getValue().getAsInt());
            for (var e : d.getAsJsonArray("owned")) owned.add(e.getAsString());
            for (var e : d.getAsJsonArray("redeemed")) redeemed.add(e.getAsString());
            quick = d.has("quick") && d.get("quick").getAsBoolean();
            auto = d.has("auto") && d.get("auto").getAsBoolean();
            multi = d.has("multi") ? Math.max(1, Math.min(MAX_MULTI, d.get("multi").getAsInt())) : 1;
            Quests.read(d);
            if (d.has("streak")) {
                streak = d.get("streak").getAsInt();
                bestStreak = d.get("best").getAsInt();
                lastDay = d.get("day").getAsLong();
                countDay = d.get("cday").getAsLong();
                dayActiveMs = d.get("dms").getAsLong();
            }
            if (d.has("history")) {
                for (var e : d.getAsJsonArray("history")) {
                    JsonObject h = e.getAsJsonObject();
                    history.add(new Drop(h.get("k").getAsString(), h.get("c").getAsString(), h.get("t").getAsLong(), h.get("d").getAsBoolean()));
                }
            }
            owned.add(STARTER);
        } catch (Exception e) {
            Zeus.LOG.warn("Zeus Crates: could not read crates.dat", e);
            starter();
        }
    }

    /** Saves on exit, but only if the crate data was ever read (never creates the file just to close). */
    public static synchronized void flush() {
        if (loaded) save(true);
    }

    public static synchronized void save(boolean now) {
        if (!now && !dirty && System.currentTimeMillis() - lastSave < 120_000L) return;
        lastSave = System.currentTimeMillis();
        dirty = false;
        JsonObject d = new JsonObject();
        d.addProperty("tokens", tokens);
        d.addProperty("keys", keys);
        d.addProperty("kv", 2);
        d.addProperty("active", activeMs);
        d.addProperty("drop", dropMs);
        d.addProperty("earned", earned);
        d.addProperty("opened", opened);
        d.addProperty("dry", dry);
        d.add("crates", new JsonObject());
        d.addProperty("dupes", dupes);
        d.addProperty("refunded", refunded);
        d.addProperty("bought", keysBought);
        JsonObject ob = new JsonObject();
        openedBy.forEach(ob::addProperty);
        d.add("openedBy", ob);
        JsonArray o = new JsonArray();
        owned.forEach(o::add);
        d.add("owned", o);
        JsonArray r = new JsonArray();
        redeemed.forEach(r::add);
        d.add("redeemed", r);
        d.addProperty("quick", quick);
        d.addProperty("auto", auto);
        d.addProperty("multi", multi);
        Quests.write(d);
        d.addProperty("streak", streak);
        d.addProperty("best", bestStreak);
        d.addProperty("day", lastDay);
        d.addProperty("cday", countDay);
        d.addProperty("dms", dayActiveMs);
        JsonArray hs = new JsonArray();
        for (Drop dr : history) {
            JsonObject h = new JsonObject();
            h.addProperty("k", dr.knife());
            h.addProperty("c", dr.crate());
            h.addProperty("t", dr.at());
            h.addProperty("d", dr.duplicate());
            hs.add(h);
        }
        d.add("history", hs);
        String body = new Gson().toJson(d);
        JsonObject root = new JsonObject();
        root.addProperty("data", body);
        root.addProperty("sig", sign(body));
        try {
            Files.createDirectories(file().getParent());
            Path tmp = file().resolveSibling("crates.dat.tmp");
            Files.writeString(tmp, new Gson().toJson(root));
            try {
                Files.move(tmp, file(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, file(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            dirty = true;
            Zeus.LOG.warn("Zeus Crates: could not save crates.dat", e);
        }
    }
}
