package mcjty.lostcities.gui;

import com.mojang.blaze3d.matrix.MatrixStack;
import mcjty.lostcities.api.LostChunkCharacteristics;
import mcjty.lostcities.api.RailChunkType;
import mcjty.lostcities.config.LostCityConfiguration;
import mcjty.lostcities.config.LostCityProfile;
import mcjty.lostcities.gui.elements.*;
import mcjty.lostcities.setup.Config;
import mcjty.lostcities.worldgen.lost.BuildingInfo;
import mcjty.lostcities.worldgen.lost.Highway;
import mcjty.lostcities.worldgen.lost.Railway;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.text.StringTextComponent;

import javax.annotation.Nullable;
import java.util.*;

public class GuiLCConfig extends Screen {

    private final Screen parent;
//    private final WorldType worldType;

    private Button profileButton;
    private Button customizeButton;
    private Button modeButton;

    private static final int YOFFSET = 21;
    private String curpage;
    private int y;

    private static final List<String> MODES = Arrays.asList("Cities", "Buildings", "Damage", "Transport", "Various");
    private String mode = MODES.get(0);

    private long seed = 3439249320423L;
    private final Random random = new Random();

    private final List<GuiElement> elements = new ArrayList<>();

    private final LostCitySetup localSetup = new LostCitySetup(this::refreshPreview);

    public GuiLCConfig(Screen parent) { // @todo 1.16}, WorldType worldType) {
        super(new StringTextComponent("Lost City Configuration"));
        this.parent = parent;
//        this.worldType = worldType;
        localSetup.copyFrom(LostCitySetup.CLIENT_SETUP);
    }

    private static void selectProfile(String profileName, @Nullable LostCityProfile profile) {
        Config.profileFromClient = profileName;
        if (profile != null) {
            LostCityConfiguration.standardProfiles.get("customized").copyFrom(profile);
            Config.jsonFromClient = profile.toJson(false).toString();
        }
    }

    public LostCitySetup getLocalSetup() {
        return localSetup;
    }

    public FontRenderer getFont() {
        return this.font;
    }

    @Override
    public void tick() {
        elements.forEach(GuiElement::tick);
    }

    @Override
    protected void init() {
        super.init();
        Objects.requireNonNull(this.minecraft).keyboardHandler.setSendRepeatsToGui(true);

        profileButton = addButton(new ButtonExt(this, 70, 10, 100, 20, new StringTextComponent(localSetup.getProfileLabel()), p -> {
            localSetup.toggleProfile(/* @todo 1.16 worldType*/);
            updateValues();
        }).tooltip(new StringTextComponent("Select a standard profile for your Lost City worldgen")));
        customizeButton = addButton(new ButtonExt(this, 180, 10, 100, 20, new StringTextComponent("Customize"), p -> {
            localSetup.customize();
            updateValues();
        }).tooltip(new StringTextComponent("Create a customized version of the currently selected profile")));
        modeButton = addButton(new ButtonExt(this, 290, 10, 100, 20, new StringTextComponent(mode), p -> toggleMode())
            .tooltip(new StringTextComponent("Switch between different configuration pages")));

        Button doneButton = addButton(new Button(10, this.height - 30, 120, 20,
                new StringTextComponent("Done"), p -> done()));
        Button cancelButton = addButton(new Button(this.width - 130, this.height - 30, 120, 20,
                new StringTextComponent("Cancel"), p -> cancel()));
        Button randomizeButton = addButton(new ButtonExt(this, this.width - 35, 35, 30, 20, new StringTextComponent("Rnd"), p -> randomizePreview())
                .tooltip(new StringTextComponent("Randomize the seed for the preview (does not affect the generated world)")));

        initCities();
        initBuildings();
        initDamage();
        initTransport();
        initVarious();

        updateValues();
    }

    private BooleanElement addBool(int left, String attribute) {
        BooleanElement el = new BooleanElement(this, curpage, left, y, attribute);
        add(el);
        return el;
    }

    private FloatElement addFloat(int left, String attribute) {
        FloatElement el = new FloatElement(this, curpage, left, y, attribute);
        add(el);
        return el;
    }

    private IntElement addInt(int left, String attribute) {
        IntElement el = new IntElement(this, curpage, left, y, attribute);
        add(el);
        return el;
    }

    private void start(String name) {
        curpage = name;
        y = 40;
    }

    private void nl() {
        y += YOFFSET;
    }

    private void initVarious() {
        start("Various");
        addBool(110, "lostcity.generateSpawners").label("Spawners:"); nl();
        addBool(110, "lostcity.generateLighting").label("Lighting:"); nl();
        addBool(110, "lostcity.generateLoot").label("Loot:"); nl();
        addFloat(110, "lostcity.vineChance").label("Vines:"); nl();
        addFloat(110, "lostcity.randomLeafBlockChance").label("Leafs:"); nl();
        nl();
        addBool(110, "lostcity.generateNether").label("Nether:");
    }

    private void initDamage() {
        start("Damage");
        addBool(70, "lostcity.rubbleLayer").label("Rubble:"); nl();

        addFloat(70, "lostcity.ruinChance").label("Ruins:").prefix("%");
        addFloat(70 + 80, "lostcity.ruinMinlevelPercent").prefix("-");
        addFloat(70 + 140, "lostcity.ruinMaxlevelPercent").prefix("+");
        nl();

        addFloat(70, "explosions.explosionChance").label("Explosion:").prefix("%");
        addInt(70 + 80, "explosions.explosionMinRadius").prefix("-");
        addInt(70 + 140, "explosions.explosionMaxRadius").prefix("+");
        nl();
        addInt(70 + 80, "explosions.explosionMinHeight").label("Height:");
        addInt(70 + 140, "explosions.explosionMaxHeight");
        nl();

        addFloat(70, "explosions.miniExplosionChance").label("Min/exp:").prefix("%");
        addInt(70 + 80, "explosions.miniExplosionMinRadius").prefix("-");
        addInt(70 + 140, "explosions.miniExplosionMaxRadius").prefix("+");
        nl();
        addInt(70 + 80, "explosions.miniExplosionMinHeight").label("Height:");
        addInt(70 + 140, "explosions.miniExplosionMaxHeight");
        nl();
    }

    private void initBuildings() {
        start("Buildings");
        addInt(110, "lostcity.buildingMinFloors").label("Floors:");
        addInt(110 + 55, "lostcity.buildingMaxFloors");
        nl();
        addInt(110, "lostcity.buildingMinFloorsChance").label("Floor Chance:");
        addInt(110 + 55, "lostcity.buildingMaxFloorsChance");
        nl();
        addInt(110, "lostcity.buildingMinCellars").label("Cellars:");
        addInt(110 + 55, "lostcity.buildingMaxCellars");
    }

    private void initTransport() {
        start("Transport");
        addFloat(110, "lostcity.highwayMainPerlinScale").label("1st perlin:"); nl();
        addFloat(110, "lostcity.highwaySecondaryPerlinScale").label("2nd perlin:"); nl();
        addFloat(110, "lostcity.highwayPerlinFactor").label("Perlin:"); nl();
        addInt(110, "lostcity.highwayDistanceMask").label("Distance mask:"); nl();
        addBool(110, "lostcity.railwaysEnabled").label("Railways:"); nl();
    }

    private void initCities() {
        start("Cities");
        addFloat(110,"cities.cityChance").label("Rarity:"); nl();
        addFloat(110,"cities.cityThreshold").label("Threshold:"); nl();

        addInt(110,"cities.cityMinRadius").label("Radius:");
        addInt(110 + 55, "cities.cityMaxRadius");
        nl();

        addFloat(110,"lostcity.buildingChance").label("Buildings:"); nl();
        addFloat(110,"lostcity.building2x2Chance").label("Buildings 2x2:"); nl();
        addFloat(110,"lostcity.parkChance").label("Parks:"); nl();
        addFloat(110,"lostcity.fountainChance").label("Fountains:"); nl();
    }

    private void toggleMode() {
        int idx = MODES.indexOf(mode);
        idx++;
        if (idx >= MODES.size()) {
            idx = 0;
        }
        mode = MODES.get(idx);
        modeButton.setMessage(new StringTextComponent(mode));
    }

    private void add(GuiElement el) {
        elements.add(el);
    }

    public <T extends Widget> void addWidget(T widget) {
        this.buttons.add(widget);
        this.children.add(widget);
    }

    private void randomizePreview() {
        seed = random.nextLong();
        refreshPreview();
    }

    public void refreshPreview() {
        BuildingInfo.cleanCache();
        Highway.cleanCache();
        Railway.cleanCache();
    }

    private void renderExtra(MatrixStack stack) {
        drawString(stack, font, "Profile:", 10, 16, 0xffffffff);
        elements.forEach(el -> el.render(stack));

        localSetup.get().ifPresent(profile -> {
            if ("Cities".equals(mode)) {
                renderPreviewMap(stack, profile, false);
            } else if ("Buildings".equals(mode)) {
                renderPreviewCity(stack, profile, false);
            } else if ("Damage".equals(mode)) {
                renderPreviewCity(stack, profile, true);
            } else if ("Transport".equals(mode)) {
                renderPreviewTransports(stack, profile);
            }
        });
    }

    private void renderPreviewTransports(MatrixStack stack, LostCityProfile profile) {
        renderPreviewMap(stack, profile, true);
        Random rand = new Random(seed);
        NullDimensionInfo diminfo = new NullDimensionInfo(profile, seed);
        for (int z = 0; z < 50; z++) {
            for (int x = 0; x < 50; x++) {
                int sx = x * 3 + this.width - 160;
                int sz = z * 3 + 50;
                int color = 0;
                Railway.RailChunkInfo type = Railway.getRailChunkType(x, z, diminfo, profile);
                if (type.getType() != RailChunkType.NONE) {
                    color = 0x99992222;
                }
                int levelX = Highway.getXHighwayLevel(x, z, diminfo, profile);
                int levelZ = Highway.getZHighwayLevel(x, z, diminfo, profile);
                if (levelX >= 0 || levelZ >= 0) {
                    if (color == 0) {
                        color = 0x99ffffff;
                    } else {
                        color = 0x99777777;
                    }
                }
                if (color != 0) {
                    fill(stack, sx, sz, sx + 3, sz + 3, color);
                }
            }
        }
    }

    private void renderPreviewCity(MatrixStack stack, LostCityProfile profile, boolean showDamage) {
        int base = 50 + 120;
        int leftRender = this.width - 157;
        fill(stack, leftRender, 50, leftRender + 150, base, 0xff0099bb);
        fill(stack, leftRender, base, leftRender + 150, 50 + 150, 0xff996633);

        float radius = 190;
        int dimHor = 10;
        int dimVer = 4;

        Random rand = new Random(seed);

        for (int x = 0; x < 14; x++) {
            float factor = 0;
            float sqdist = (x * 16 - 7 * 16) * (x * 16 - 7 * 16);
            if (sqdist < radius * radius) {
                float dist = (float) Math.sqrt(sqdist);
                factor = (radius - dist) / radius;
            }
            if (factor > 0 && x > 0) {
                int maxfloors = profile.BUILDING_MAXFLOORS;
                int randdist = (int) (profile.BUILDING_MINFLOORS_CHANCE + (factor + .1f) * (profile.BUILDING_MAXFLOORS_CHANCE - profile.BUILDING_MINFLOORS_CHANCE));
                if (randdist < 1) {
                    randdist = 1;
                }
                int f = profile.BUILDING_MINFLOORS + rand.nextInt(randdist);
                f++;
                if (f > maxfloors+1) {
                    f = maxfloors+1;
                }
                int minfloors = profile.BUILDING_MINFLOORS + 1;
                if (f < minfloors) {
                    f = minfloors;
                }
                for (int i = 0; i < f; i++) {
                    fill(stack, leftRender + dimHor * x, base - i * dimVer - dimVer, leftRender + dimHor * x + dimHor - 1, base - i * dimVer + dimVer - 1 - dimVer, 0xffffffff);
                }

                int maxcellars = profile.BUILDING_MAXCELLARS;
                int fb = profile.BUILDING_MINCELLARS + ((maxcellars <= 0) ? 0 : rand.nextInt(maxcellars + 1));
                for (int i = 0; i < fb; i++) {
                    fill(stack, leftRender + dimHor * x, base + i * dimVer, leftRender + dimHor * x + dimHor - 1, base + i * dimVer + dimVer - 1, 0xff333333);
                }
            }
        }

//        profile.EXPLOSION_CHANCE
        if (showDamage) {
            float horFactor = 1.0f * dimHor / 16.0f;
            float verFactor = 1.0f * dimVer / 6.0f;
            int cx = leftRender + 75;
            int cz = (int) (base - (profile.EXPLOSION_MINHEIGHT-65) * verFactor);
            Random rnd = new Random(333);
            int explosionRadius = profile.EXPLOSION_MAXRADIUS;
            for (int x = (int) (cx - explosionRadius * horFactor); x <= cx + explosionRadius * horFactor; x++) {
                for (int z = (int) (cz - explosionRadius * verFactor); z <= cz + explosionRadius * verFactor; z++) {
                    double sqdist = (cx - x) * (cx - x) / horFactor / horFactor + (cz - z) * (cz - z) / verFactor / verFactor;
                    double dist = Math.sqrt(sqdist);
                    if (dist < explosionRadius - 3) {
                        double damage = 3.0f * (explosionRadius - dist) / explosionRadius;
                        if (rnd.nextFloat() < damage) {
                            fill(stack, x, z, x + 1, z + 1, 0x66ff0000);
                        }
                    }
                }
            }
            cx = leftRender + 35;
            cz = (int) (base - (profile.MINI_EXPLOSION_MINHEIGHT-65) * verFactor);
            explosionRadius = profile.MINI_EXPLOSION_MAXRADIUS;
            for (int x = (int) (cx - explosionRadius * horFactor); x <= cx + explosionRadius * horFactor; x++) {
                for (int z = (int) (cz - explosionRadius * verFactor); z <= cz + explosionRadius * verFactor; z++) {
                    double sqdist = (cx - x) * (cx - x) / horFactor / horFactor + (cz - z) * (cz - z) / verFactor / verFactor;
                    double dist = Math.sqrt(sqdist);
                    if (dist < explosionRadius - 3) {
                        double damage = 3.0f * (explosionRadius - dist) / explosionRadius;
                        if (rnd.nextFloat() < damage) {
                            fill(stack, x, z, x + 1, z + 1, 0x66ff0000);
                        }
                    }
                }
            }
        }
    }

    private static int soften(int color, boolean soft) {
        if (soft) {
            int r = (color & 0xff0000) >> 16;
            int g = (color & 0xff00) >> 8;
            int b = (color & 0xff);
            return (r / 3) << 16 | (g / 3) << 8 | (b / 3);
        }
        return color;
    }

    private void renderPreviewMap(MatrixStack stack, LostCityProfile profile, boolean soft) {
        NullDimensionInfo diminfo = new NullDimensionInfo(profile, seed);
        for (int z = 0; z < 50; z++) {
            for (int x = 0; x < 50; x++) {
                int sx = x * 3 + this.width - 160;
                int sz = z * 3 + 50;
                int color = 0x005500;
                char b = diminfo.getBiomeChar(x, z);
                switch (b) {
                    case 'p':
                        break;
                    case '-':
                    case '=':
                        color = 0x000066; break;
                    case '#': color = 0x447744; break;
                    case '+': color = 0x335533; break;
                    case '*':
                    case 'd':
                        color = 0xcccc55; break;
                }
                fill(stack, sx, sz, sx + 3, sz + 3, 0xff000000 + soften(color, soft));
                LostChunkCharacteristics characteristics = BuildingInfo.getChunkCharacteristics(x, z, diminfo);
                if (characteristics.isCity) {
                    color = 0x995555;
                    if (BuildingInfo.hasBuildingGui(x, z, diminfo, characteristics)) {
                        color = 0xffffff;
                    }
                    fill(stack, sx, sz, sx + 2, sz + 2, 0xff000000 + soften(color, soft));
                }
            }
        }
    }

    private void updateValues() {
        elements.forEach(GuiElement::update);
        refreshPreview();
    }

    private void refreshButtons() {
        profileButton.setMessage(new StringTextComponent(localSetup.getProfileLabel()));
        customizeButton.active = localSetup.isCustomizable();

        boolean isCustomized = "customized".equals(localSetup.getProfileLabel());
        modeButton.active = localSetup.isCustomizable() || isCustomized;
        elements.forEach(s -> {
            s.setEnabled(isCustomized);
            s.setBasedOnMode(mode);
        });
    }


    private void cancel() {
        refreshPreview();
        Minecraft.getInstance().setScreen(parent);
    }

    private void done() {
        refreshPreview();
        LostCitySetup.CLIENT_SETUP.copyFrom(localSetup);
        LostCityProfile customizedProfile = localSetup.getCustomizedProfile();
        if ("customized".equals(localSetup.getProfile()) && customizedProfile != null) {
            LostCityConfiguration.standardProfiles.get("customized").copyFrom(customizedProfile);
            selectProfile(localSetup.getProfile(), customizedProfile);
        } else {
            selectProfile(localSetup.getProfile(), null);
        }

        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(MatrixStack stack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(stack);
        refreshButtons();
        renderExtra(stack);
        super.render(stack, mouseX, mouseY, partialTicks);
        for(Widget widget : this.buttons) {
            if (widget.isMouseOver(mouseX, mouseY) && widget.visible) {
                widget.renderToolTip(stack, mouseX, mouseY);
                break;
            }
        }
    }
}
