package mcjty.lostcities.worldgen.lost;

import mcjty.lostcities.api.*;
import mcjty.lostcities.config.LostCityProfile;
import mcjty.lostcities.varia.ChunkCoord;
import mcjty.lostcities.varia.Counter;
import mcjty.lostcities.varia.QualityRandom;
import mcjty.lostcities.worldgen.ChunkHeightmap;
import mcjty.lostcities.worldgen.IDimensionInfo;
import mcjty.lostcities.worldgen.LostCityTerrainFeature;
import mcjty.lostcities.worldgen.lost.cityassets.*;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.apache.commons.lang3.Validate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class BuildingInfo implements ILostChunkInfo {
    public final int chunkX;
    public final int chunkZ;
    public final ChunkCoord coord;
    public final IDimensionInfo provider;
    public final LostCityProfile profile;       // Profile cactive for this chunk: can be different in city sphere worlds

    public final boolean outsideChunk;  // Only used for citysphere worlds and when this chunk is outside
    public final int groundLevel;
    public final int waterLevel;

    public final boolean isCity;
    public final boolean hasBuilding;
    public final int building2x2Section;    // -1 for not, 0 for top left, 1 for top right, 2 for bottom left, 3 for bottom right

    public final ILostCityMultiBuilding multiBuilding;
    public final ILostCityBuilding buildingType;
    public final BuildingPart fountainType;
    public final BuildingPart parkType;
    public final BuildingPart bridgeType;
    public final BuildingPart stairType;
    public final BuildingPart frontType;
    private final float stairPriority;      // A random number that indicates if this chunk should get a stair if there are competing stairs around it. The highest wins
    public final BuildingPart railDungeon;    // Dungeon next to rails. Will only generate if there are actually rails next to it
    public final StreetType streetType;
    private final int floors;
    public final int floorsBelowGround;
    public final BuildingPart[] floorTypes;
    public final BuildingPart[] floorTypes2;
    public final boolean[] connectionAtX;
    public final boolean[] connectionAtZ;
    public final boolean noLoot;
    public final float ruinHeight;      // The height (as a percentage between 0 and 1) at which we focus the ruin layer. Set to -1 if this building is not ruined

    public final int highwayXLevel;     // 0 or 1 if there is a highway at this chunk
    public final int highwayZLevel;     // 0 or 1 if there is a highway at this chunk
    public final int cityLevel;         // The first floor of buildings starts at groundLevel + cityLevel * 6

    public final boolean xBridge;       // A boolean indicating that this chunk is a candidate for holding a bridge (no guarantee)
    public final boolean zBridge;       // A boolean indicating that this chunk is a candidate for holding a bridge (no guarantee)

    public final boolean xRailCorridor; // A boolean indicating that this chunk is a candidate for holding a corridor (no guarantee)
    public final boolean zRailCorridor; // A boolean indicating that this chunk is a candidate for holding a corridor (no guarantee)

    public final Block doorBlock;

    // Transient info that is calculated on demand
    private BuildingInfo xmin = null;
    private BuildingInfo xmax = null;
    private BuildingInfo zmin = null;
    private BuildingInfo zmax = null;
    private DamageArea damageArea = null;
    private Palette palette = null;
    private CompiledPalette compiledPalette = null;
    private Boolean isOcean = null;

    private boolean xBridgeTypeCalculated = false;
    private boolean zBridgeTypeCalculated = false;
    private BuildingPart xBridgeType = null;
    private BuildingPart zBridgeType = null;

    private boolean stairsCalculated = false;
    private Direction stairDirection;
    private boolean actualStairsCalculated = false;
    private Direction actualStairDirection;

    private Boolean horizontalMonorail = null;
    private Boolean verticalMonorail = null;

    private Integer[] desiredTerrainCorrectionHeights = null;
    private Integer[] desiredMaxHeight1 = null;

    // A list of todo's
    private final List<BlockPos> lightingUpdateTodo = new ArrayList<>();
    private final List<BlockPos> torchTodo = new ArrayList<>();
    private final List<BlockPos> saplingTodo = new ArrayList<>();

    public static class ConditionTodo {
        private final String condition;
        private final String part;
        private final String building;

        public ConditionTodo(String condition, String part, BuildingInfo info) {
            this.part = part == null ? "<none>" : part;
            this.condition = condition;
            if (info.hasBuilding) {
                this.building = info.getBuildingType();
            } else {
                this.building = "<none>";
            }
        }

        public String getCondition() {
            return condition;
        }

        public String getPart() {
            return part;
        }

        public String getBuilding() {
            return building;
        }
    }

    // BuildingInfo cache
    private static final Map<ChunkCoord, BuildingInfo> buildingInfoMap = new HashMap<>();
    private static final Map<ChunkCoord, LostChunkCharacteristics> cityInfoMap = new HashMap<>();

    public void addSaplingTodo(BlockPos pos) {
        saplingTodo.add(pos);
    }

    public List<BlockPos> getSaplingTodo() {
        return saplingTodo;
    }

    public void clearSaplingTodo() {
        saplingTodo.clear();
    }

    public void addTorchTodo(BlockPos index) {
        torchTodo.add(index);
    }

    public List<BlockPos> getTorchTodo() {
        return torchTodo;
    }

    public void clearTorchTodo() {
        torchTodo.clear();
    }

    public void addLightingUpdateTodo(BlockPos pos) {
        lightingUpdateTodo.add(pos);
    }

    public List<BlockPos> getLightingUpdateTodo() {
        return lightingUpdateTodo;
    }

    public void clearLightingUpdateTodo() {
        lightingUpdateTodo.clear();
    }

    public CompiledPalette getCompiledPalette() {
        if (compiledPalette == null) {
            compiledPalette = new CompiledPalette(palette);
        }
        return compiledPalette;
    }

    public DamageArea getDamageArea() {
        if (damageArea == null) {
            damageArea = new DamageArea(chunkX, chunkZ, provider, this);
        }
        return damageArea;
    }

    /**
     * Based on which part of the chunk we have something for we find the correct
     * info object where we have to add the todo.
     */
    public BuildingInfo getTodoChunk(int x, int z) {
        if (x >= 8 && z >= 8) {
            return this;
        } else if (x < 8 && z >= 8) {
            return getXmin();
        } else if (x >= 8) {
            return getZmin();
        } else {
            return getXmin().getZmin();
        }
    }

    public Style getOutsideStyle() {
        return AssetRegistries.STYLES.get(provider.getWorldStyle().getOutsideStyle());
    }

    private void createPalette(Random rand) {
        Style style;
        if (!isCity) {
            style = getOutsideStyle();
        } else {
            String name = getCityStyle().getStyle();
            style = AssetRegistries.STYLES.get(name);
            if (style == null) {
                throw new RuntimeException("Cannot find style '" + name + "'!");
            }
        }
        palette = style.getRandomPalette(provider, rand);
    }

    // x between 0 and 15, z between 0 and 15
    public BuildingInfo getAdjacent(int x, int z) {
        if (x == 0) {
            return getXmin();
        } else if (x == 15) {
            return getXmax();
        } else if (z == 0) {
            return getZmin();
        } else if (z == 15) {
            return getZmax();
        } else {
            return null;
        }
    }

    public BuildingInfo getXmin() {
        if (xmin == null) {
            xmin = getBuildingInfo(chunkX - 1, chunkZ, provider);
        }
        return xmin;
    }

    public BuildingInfo getXmax() {
        if (xmax == null) {
            xmax = getBuildingInfo(chunkX + 1, chunkZ, provider);
        }
        return xmax;
    }

    public BuildingInfo getZmin() {
        if (zmin == null) {
            zmin = getBuildingInfo(chunkX, chunkZ - 1, provider);
        }
        return zmin;
    }

    public BuildingInfo getZmax() {
        if (zmax == null) {
            zmax = getBuildingInfo(chunkX, chunkZ + 1, provider);
        }
        return zmax;
    }

    public int getMaxHeight() {
        if (hasBuilding) {
            return getCityGroundLevel() + floors * 6;
        } else {
            int m = getMaxHighwayLevel();
            if (m >= 0) {
                return groundLevel + m * 6;
            } else {
                return getCityGroundLevel();
            }
        }
    }

    public int getCityGroundLevel() {
        return groundLevel + cityLevel * 6;
    }

    /**
     * Get the city ground level but lower the level outside cities
     */
    public int getCityGroundLevelOutsideLower() {
        if (isCity) {
            return groundLevel + cityLevel * 6;
        } else {
            return groundLevel + cityLevel * 6 -1;
        }
    }

    public boolean isValidFloor(int l) {
        return (l + floorsBelowGround) >= 0 && (l + floorsBelowGround) < floorTypes.length;
    }

    public BuildingPart getFloor(int l) {
        return floorTypes[l + floorsBelowGround];
    }

    public BuildingPart getFloorPart2(int l) {
        return floorTypes2[l + floorsBelowGround];
    }

    public ILostCityBuilding getBuilding() {
        return buildingType;
    }

    public CityStyle getCityStyle() {
        return (CityStyle) getChunkCharacteristics(chunkX, chunkZ, provider).cityStyle;
    }

    // Version for usage inside the gui
    public static boolean hasBuildingGui(int chunkX, int chunkZ, IDimensionInfo provider, LostChunkCharacteristics characteristics) {
        Random rand = getBuildingRandom(chunkX, chunkZ, provider.getSeed());
        rand.nextFloat();       // Compatibility?

        int building2x2Section = characteristics.section;
        boolean b = characteristics.couldHaveBuilding;
        if (b && building2x2Section < 0) {
            if (rand.nextFloat() < getChunkCharacteristics(chunkX - 1, chunkZ, provider).buildingType.getPrefersLonely()) {
                b = false;
            } else if (rand.nextFloat() < getChunkCharacteristics(chunkX + 1, chunkZ, provider).buildingType.getPrefersLonely()) {
                b = false;
            } else if (rand.nextFloat() < getChunkCharacteristics(chunkX, chunkZ - 1, provider).buildingType.getPrefersLonely()) {
                b = false;
            } else if (rand.nextFloat() < getChunkCharacteristics(chunkX, chunkZ + 1, provider).buildingType.getPrefersLonely()) {
                b = false;
            }
        }
        return b;
    }

    public static synchronized LostChunkCharacteristics getChunkCharacteristics(int chunkX, int chunkZ, IDimensionInfo provider) {
        RegistryKey<World> type = provider.getType();
        ChunkCoord key = new ChunkCoord(type, chunkX, chunkZ);
        if (cityInfoMap.containsKey(key)) {
            return cityInfoMap.get(key);
        } else {
            LostCityProfile profile = getProfile(chunkX, chunkZ, provider);
            LostChunkCharacteristics characteristics = new LostChunkCharacteristics();

            characteristics.isCity = isCityRaw(chunkX, chunkZ, provider, profile);
            characteristics.section = getMultiBuildingSection(chunkX, chunkZ, provider, profile);
            if (characteristics.section > 0) {
                characteristics.cityLevel = getTopLeftCityInfo(characteristics, chunkX, chunkZ, provider).cityLevel;
            } else {
                characteristics.cityLevel = getCityLevel(chunkX, chunkZ, provider);
            }
            Random rand = getBuildingRandom(chunkX, chunkZ, provider.getSeed());
            characteristics.couldHaveBuilding = characteristics.isCity && checkBuildingPossibility(chunkX, chunkZ, provider, profile, characteristics.section, characteristics.cityLevel, rand);
            if (profile.isSpace() && characteristics.section == -1) {
                // Minimize cities at the edge of the city in an orb
                float dist = CitySphere.getRelativeDistanceToCityCenter(chunkX, chunkZ, provider);
                if (dist > .7f) {
                    characteristics.couldHaveBuilding = false;
                }
            }

            ChunkCoord coord = new ChunkCoord(type, chunkX, chunkZ);
            CityStyle cityStyle;
            // If this is a street we find other chunks connected to this and pick the cityStyle
            // that represents the majority. This is to prevent streets from switching style randomly if two
            // different styled cities mix
            if (characteristics.isCity && !characteristics.couldHaveBuilding) {
                Counter<String> counter = new Counter<>();
                for (int cx = -1 ; cx <= 1 ; cx++) {
                    for (int cz = -1 ; cz <= 1 ; cz++) {
                        cityStyle = City.getCityStyle(coord.getChunkX()+cx, coord.getChunkZ()+cz, provider, profile);
                        counter.add(cityStyle.getName());
                        if (cx == 0 && cz == 0) {
                            counter.add(cityStyle.getName());   // Add this chunk again for a bias
                        }
                    }
                }
                cityStyle = AssetRegistries.CITYSTYLES.get(counter.getMostOccuring());
            } else {
                cityStyle = City.getCityStyle(chunkX, chunkZ, provider, profile);
            }
            characteristics.cityStyle = cityStyle;


            if (characteristics.section >= 1) {
                LostChunkCharacteristics topleft = getTopLeftCityInfo(characteristics, chunkX, chunkZ, provider);
                characteristics.multiBuilding = topleft.multiBuilding;
                if (characteristics.multiBuilding != null) {
                    switch (characteristics.section) {
                        case 1:
                            characteristics.buildingType = AssetRegistries.BUILDINGS.get(characteristics.multiBuilding.getBuilding(1, 0));
                            break;
                        case 2:
                            characteristics.buildingType = AssetRegistries.BUILDINGS.get(characteristics.multiBuilding.getBuilding(0, 1));
                            break;
                        case 3:
                            characteristics.buildingType = AssetRegistries.BUILDINGS.get(characteristics.multiBuilding.getBuilding(1, 1));
                            break;
                        default:
                            throw new RuntimeException("What 2!");
                    }
                } else {
                    characteristics.buildingType = topleft.buildingType;
                }
            } else {
                PredefinedCity.PredefinedBuilding predefinedBuilding = City.getPredefinedBuilding(chunkX, chunkZ, type);
                if (characteristics.section == 0) {
                    String name = cityStyle.getRandomMultiBuilding(rand);
                    if (predefinedBuilding != null) {
                        name = predefinedBuilding.getBuilding();
                    }
                    characteristics.multiBuilding = AssetRegistries.MULTI_BUILDINGS.get(name);
                    characteristics.buildingType = AssetRegistries.BUILDINGS.get(characteristics.multiBuilding.getBuilding(0, 0));
                } else {
                    characteristics.multiBuilding = null;
                    String name = cityStyle.getRandomBuilding(rand);
                    if (predefinedBuilding != null) {
                        name = predefinedBuilding.getBuilding();
                    }
                    characteristics.buildingType = AssetRegistries.BUILDINGS.get(name);
                }
            }

            // @todo 1.14

            cityInfoMap.put(key, characteristics);
            return characteristics;
        }
    }

    /**
     * Don't use the cache as we're busy building the cache.
     */
    public static boolean isCityRaw(int chunkX, int chunkZ, IDimensionInfo provider, LostCityProfile profile) {
        if (isVoidChunk(chunkX, chunkZ, provider)) {
            // If we have a void chunk then no city here
            return false;
        } else if (provider.getProfile().isSpace()) {
            if (CitySphere.onCitySphereBorder(chunkX, chunkZ, provider)) {
                return false;
            } else if (!provider.getProfile().CITYSPHERE_LANDSCAPE_OUTSIDE && !CitySphere.fullyInsideCitySpere(chunkX, chunkZ, provider)) {
                return false;
            } else if (CitySphere.hasMonorailStation(chunkX, chunkZ, provider)) {
                return false;
            }
        }

        float cityFactor = City.getCityFactor(chunkX, chunkZ, provider, profile);
        return cityFactor > profile.CITY_THRESHOLD;
    }

    public static boolean isCity(int chunkX, int chunkZ, IDimensionInfo provider) {
        return getChunkCharacteristics(chunkX, chunkZ, provider).isCity;
    }

    private static boolean checkBuildingPossibility(int chunkX, int chunkZ, IDimensionInfo provider, LostCityProfile profile, int section, int cityLevel, Random rand) {
        boolean b;
        float bc = rand.nextFloat();
        RegistryKey<World> type = provider.getType();

        PredefinedCity.PredefinedBuilding predefinedBuilding = City.getPredefinedBuilding(chunkX, chunkZ, type);
        if (predefinedBuilding != null) {
            return true;    // We don't need other tests
        }
        PredefinedCity.PredefinedStreet predefinedStreet = City.getPredefinedStreet(chunkX, chunkZ, type);
        if (predefinedStreet != null) {
            return false;   // No building here
        }

        if (section >= 0) {
            // Part of multi-building. We have checked everything above
            b = true;
        } else if (bc >= profile.BUILDING_CHANCE) {
            // Random says we should have no building here
            b = false;
        } else if (hasHighway(chunkX, chunkZ, provider, profile)) {
            // We are above a highway. Check if we have room for a building
            int maxh = Math.max(Highway.getXHighwayLevel(chunkX, chunkZ, provider, profile), Highway.getZHighwayLevel(chunkX, chunkZ, provider, profile));
            b = cityLevel > maxh+1;       // Allow a building if it is higher then the maximum highway + one
            // Later we will take care to make sure we don't have too many cellars
            // Note that for easy of coding we still disallow multi-buildings above highways
        } else if (hasRailway(chunkX, chunkZ, provider, profile)) {
            // We are above a railway. Check if we have room for a building
            Railway.RailChunkInfo info = Railway.getRailChunkType(chunkX, chunkZ, provider, profile);
            if (info.getType() == RailChunkType.STATION_UNDERGROUND) {
                b = false;  // No building directly above the underground station
            } else {
                int maxh = info.getLevel();
                b = cityLevel > maxh + 1;       // Allow a building if it is higher then the maximum railway + one
                // Later we will take care to make sure we don't have too many cellars
                // Note that for easy of coding we still disallow multi-buildings above railways
            }
        } else {
            // General case
            b = true;
        }
        return b;
    }

    private static int getMultiBuildingSection(int chunkX, int chunkZ, IDimensionInfo provider, LostCityProfile profile) {
        int section;
        if (isTopLeftOf2x2Building(chunkX, chunkZ, provider, profile)) {
            section = 0;
        } else if (isTopLeftOf2x2Building(chunkX - 1, chunkZ, provider, profile)) {
            section = 1;
        } else if (isTopLeftOf2x2Building(chunkX, chunkZ - 1, provider, profile)) {
            section = 2;
        } else if (isTopLeftOf2x2Building(chunkX - 1, chunkZ - 1, provider, profile)) {
            section = 3;
        } else {
            section = -1;
        }
        return section;
    }

    private BuildingInfo calculateTopLeft() {
        switch (building2x2Section) {
            case 0:
                return this;
            case 1:
                return getXmin();
            case 2:
                return getZmin();
            case 3:
                return getXmin().getZmin();
            default:
                throw new RuntimeException("What!");
        }
    }

    private static LostChunkCharacteristics getTopLeftCityInfo(LostChunkCharacteristics thisone, int chunkX, int chunkZ, IDimensionInfo provider) {
        switch (thisone.section) {
            case 0:
                return thisone;
            case 1:
                return getChunkCharacteristics(chunkX-1, chunkZ, provider);
            case 2:
                return getChunkCharacteristics(chunkX, chunkZ-1, provider);
            case 3:
                return getChunkCharacteristics(chunkX-1, chunkZ-1, provider);
            default:
                throw new RuntimeException("What!");
        }
    }

    private static boolean isCandidateForTopLeftOf2x2Building(int chunkX, int chunkZ, IDimensionInfo provider, LostCityProfile profile) {
        RegistryKey<World> type = provider.getType();
        PredefinedCity.PredefinedBuilding predefinedBuilding = City.getPredefinedBuilding(chunkX, chunkZ, type);
        if (predefinedBuilding != null && predefinedBuilding.isMulti()) {
            return true;    // We don't need other tests. This is the top-left of a multibuilding
        }
        PredefinedCity.PredefinedStreet predefinedStreet = City.getPredefinedStreet(chunkX, chunkZ, type);
        if (predefinedStreet != null) {
            return false;   // There is a street here so no building
        }
        if (isMultiBuildingCandidate(chunkX, chunkZ, provider, profile)) {
            Random rand = getBuildingRandom(chunkX, chunkZ, provider.getSeed());
            return rand.nextFloat() < profile.BUILDING2X2_CHANCE;
        } else {
            return false;
        }
    }

    private static boolean isMultiBuildingCandidate(int chunkX, int chunkZ, IDimensionInfo provider, LostCityProfile profile) {
        return isCityRaw(chunkX, chunkZ, provider, profile) && !hasHighway(chunkX, chunkZ, provider, profile) && !hasRailway(chunkX, chunkZ, provider, profile);
    }

    private static boolean hasHighway(int chunkX, int chunkZ, IDimensionInfo provider, LostCityProfile profile) {
        return Highway.getXHighwayLevel(chunkX, chunkZ, provider, profile) >= 0 || Highway.getZHighwayLevel(chunkX, chunkZ, provider, profile) >= 0;
    }

    private static boolean hasRailway(int chunkX, int chunkZ, IDimensionInfo provider, LostCityProfile profile) {
        return Railway.getRailChunkType(chunkX, chunkZ, provider, profile).getType() != RailChunkType.NONE;
    }

    public Railway.RailChunkInfo getRailInfo() {
        return Railway.getRailChunkType(chunkX, chunkZ, provider, profile);
    }

    // Return true if a highway at this level would be a tunnel
    public boolean isTunnel(int level) {
        if (isCity) {
            // We need a tunnel if the city goes above this level
            return cityLevel > level;
        }

        // Get the (possbily cached) heightmap for this chunk
        ChunkHeightmap heightmap = provider.getHeightmap(chunkX, chunkZ);
        // The height at which the highway would be + a threshold of 3
        int highwayHeight = groundLevel + level * 6 + 3;
        // If there are many places in the chunk above this height we will need a tunnel
        int cnt = 0;
        for (int x = 2 ; x < 16 ; x += 3) {
            for (int z = 2 ; z < 16 ; z += 3) {
                if (heightmap.getHeight(x, z) > highwayHeight) {
                    cnt++;
                }
            }
        }
        return cnt > 12;    // We make a tunnel if more then half of the chunk is above the highway
    }

    private static boolean isTopLeftOf2x2Building(int chunkX, int chunkZ, IDimensionInfo provider, LostCityProfile profile) {
        RegistryKey<World> type = provider.getType();
        PredefinedCity.PredefinedBuilding predefinedBuilding = City.getPredefinedBuilding(chunkX, chunkZ, type);
        if (predefinedBuilding != null && predefinedBuilding.isMulti()) {
            // Regardless of other conditions, this is the top left of a multibuilding
            return true;
        }

        if (isCandidateForTopLeftOf2x2Building(chunkX, chunkZ, provider, profile) &&
                !isCandidateForTopLeftOf2x2Building(chunkX - 1, chunkZ, provider, profile) &&
                !isCandidateForTopLeftOf2x2Building(chunkX - 1, chunkZ - 1, provider, profile) &&
                !isCandidateForTopLeftOf2x2Building(chunkX, chunkZ - 1, provider, profile) &&

                !isCandidateForTopLeftOf2x2Building(chunkX + 1, chunkZ - 1, provider, profile) &&
                !isCandidateForTopLeftOf2x2Building(chunkX + 1, chunkZ, provider, profile) &&
                !isCandidateForTopLeftOf2x2Building(chunkX + 1, chunkZ + 1, provider, profile) &&
                !isCandidateForTopLeftOf2x2Building(chunkX, chunkZ + 1, provider, profile) &&
                !isCandidateForTopLeftOf2x2Building(chunkX - 1, chunkZ + 1, provider, profile)
                ) {
            PredefinedCity.PredefinedStreet predefinedStreet = City.getPredefinedStreet(chunkX, chunkZ, type);
            if (predefinedStreet != null) {
                return false;   // There is a street here so no building
            }
            return isMultiBuildingCandidate(chunkX + 1, chunkZ, provider, profile) &&
                    isMultiBuildingCandidate(chunkX + 1, chunkZ + 1, provider, profile) &&
                    isMultiBuildingCandidate(chunkX, chunkZ + 1, provider, profile);
        } else {
            return false;
        }
    }

    public static void cleanCache() {
        buildingInfoMap.clear();
        cityInfoMap.clear();
    }

    public static synchronized  BuildingInfo getBuildingInfo(int chunkX, int chunkZ, IDimensionInfo provider) {
        ChunkCoord key = new ChunkCoord(provider.getType(), chunkX, chunkZ);
        if (buildingInfoMap.containsKey(key)) {
            return buildingInfoMap.get(key);
        }
        BuildingInfo info = new BuildingInfo(chunkX, chunkZ, provider);
        buildingInfoMap.put(key, info);
        return info;
    }

    /**
     * Find the correct profile for this chunk. This takes space sphere worlds into account
     */
    public static LostCityProfile getProfile(int chunkX, int chunkZ, IDimensionInfo provider) {
        if (provider.getProfile().isSpace()) {
            if (CitySphere.intersectsWithCitySphere(chunkX, chunkZ, provider)) {
                return provider.getProfile();
            } else {
                return provider.getOutsideProfile();
            }
        } else {
            return provider.getProfile();
        }
    }

    private BuildingInfo(int chunkX, int chunkZ, IDimensionInfo provider) {
        this.provider = provider;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        RegistryKey<World> type = provider.getType();
        this.coord = new ChunkCoord(type, chunkX, chunkZ);

        outsideChunk = provider.getProfile().isSpace() && !CitySphere.intersectsWithCitySphere(chunkX, chunkZ, provider);
        profile = getProfile(chunkX, chunkZ, provider);

        LostChunkCharacteristics characteristics = getChunkCharacteristics(chunkX, chunkZ, provider);

        isCity = characteristics.isCity;
        building2x2Section = characteristics.section;
        cityLevel = characteristics.cityLevel;
        buildingType = characteristics.buildingType;
        multiBuilding = characteristics.multiBuilding;

        Random rand = getBuildingRandom(chunkX, chunkZ, provider.getSeed());
        rand.nextFloat();       // Compatibility?

        boolean b = characteristics.couldHaveBuilding;
        if (b && building2x2Section < 0) {
            if (rand.nextFloat() < getChunkCharacteristics(chunkX - 1, chunkZ, provider).buildingType.getPrefersLonely()) {
                b = false;
            } else if (rand.nextFloat() < getChunkCharacteristics(chunkX + 1, chunkZ, provider).buildingType.getPrefersLonely()) {
                b = false;
            } else if (rand.nextFloat() < getChunkCharacteristics(chunkX, chunkZ - 1, provider).buildingType.getPrefersLonely()) {
                b = false;
            } else if (rand.nextFloat() < getChunkCharacteristics(chunkX, chunkZ + 1, provider).buildingType.getPrefersLonely()) {
                b = false;
            }
        }

        hasBuilding = b;

        int wl;
        if (outsideChunk && provider.getProfile().CITYSPHERE_LANDSCAPE_OUTSIDE) {
            groundLevel = provider.getOutsideProfile().GROUNDLEVEL;
            wl = provider.getOutsideProfile().SEALEVEL;
        } else {
            groundLevel = provider.getProfile().GROUNDLEVEL;
            wl = provider.getProfile().SEALEVEL;
        }
        waterLevel = wl == -1 ? provider.getWorld().getSeaLevel() : wl;

        CityStyle cs = (CityStyle) characteristics.cityStyle;

        // In a 2x2 building we copy all information from the top-left chunk
        if (building2x2Section >= 1) {
            BuildingInfo topleft = calculateTopLeft();
            highwayXLevel = topleft.highwayXLevel;
            highwayZLevel = topleft.highwayZLevel;
            streetType = topleft.streetType;
            fountainType = topleft.fountainType;
            parkType = topleft.parkType;
            floors = topleft.floors;
            floorsBelowGround = topleft.floorsBelowGround;
            doorBlock = topleft.doorBlock;
            bridgeType = topleft.bridgeType;
            stairType = topleft.stairType;
            stairPriority = topleft.stairPriority;
            palette = topleft.palette;
            compiledPalette = topleft.getCompiledPalette();
            noLoot = topleft.noLoot;
            ruinHeight = topleft.ruinHeight;
        } else {
            PredefinedCity.PredefinedBuilding predefinedBuilding = City.getPredefinedBuilding(chunkX, chunkZ, type);
            highwayXLevel = Highway.getXHighwayLevel(chunkX, chunkZ, provider, profile);
            highwayZLevel = Highway.getZHighwayLevel(chunkX, chunkZ, provider, profile);

            if (rand.nextDouble() < profile.PARK_CHANCE) {
                streetType = StreetType.values()[rand.nextInt(StreetType.values().length)];
            } else {
                streetType = StreetType.NORMAL;
            }
            if (rand.nextFloat() < profile.FOUNTAIN_CHANCE) {
                fountainType = AssetRegistries.PARTS.get(cs.getRandomFountain(rand));
            } else {
                fountainType = null;
            }
            parkType = AssetRegistries.PARTS.get(cs.getRandomPark(rand));
            float cityFactor = City.getCityFactor(chunkX, chunkZ, provider, profile);

            int maxfloors = getMaxfloors(provider, cs);
            int f = profile.BUILDING_MINFLOORS + rand.nextInt((int) (profile.BUILDING_MINFLOORS_CHANCE + (cityFactor + .1f) * (profile.BUILDING_MAXFLOORS_CHANCE - profile.BUILDING_MINFLOORS_CHANCE)));
            f++;
            if (f > maxfloors+1) {
                f = maxfloors+1;
            }
            int minfloors = getMinfloors(provider, cs);
            if (f < minfloors) {
                f = minfloors;
            }

            if (provider.getProfile().isSpace() && CitySphere.intersectsWithCitySphere(chunkX, chunkZ, provider)) {
                float reldest = CitySphere.getRelativeDistanceToCityCenter(chunkX, chunkZ, provider);
                if (reldest > .6f) {
                    f = Math.max(minfloors, f-2);
                } else if (reldest > .5f) {
                    f = Math.max(minfloors, f-1);
                }
            }

            floors = f;

            int maxcellars = getMaxcellars(cs);
            int fb = profile.BUILDING_MINCELLARS + ((maxcellars <= 0) ? 0 : rand.nextInt(maxcellars+1));
            if (getMaxHighwayLevel() >= 0) {
                // If we are above a highway we make sure we can't have too many cellars
                fb = Math.min(cityLevel-getMaxHighwayLevel()-1, fb);
                if (fb < 0) {
                    fb = 0;
                }
            }
            floorsBelowGround = fb;

            doorBlock = getRandomDoor(rand);
            bridgeType = AssetRegistries.PARTS.get(cs.getRandomBridge(rand));
            stairType = AssetRegistries.PARTS.get(cs.getRandomStair(rand));
            stairPriority = rand.nextFloat();
            createPalette(rand);
            float r = rand.nextFloat();
            noLoot = building2x2Section == -1 && r < profile.BUILDING_WITHOUT_LOOT_CHANCE;
            r = rand.nextFloat();
            if (rand.nextFloat() < profile.RUIN_CHANCE && (predefinedBuilding == null || !predefinedBuilding.isPreventRuins())) {
                ruinHeight = profile.RUIN_MINLEVEL_PERCENT + (profile.RUIN_MAXLEVEL_PERCENT - profile.RUIN_MINLEVEL_PERCENT) * r;
            } else {
                ruinHeight = -1;
            }
        }

        floorTypes = new BuildingPart[floors + floorsBelowGround + 1];
        floorTypes2 = new BuildingPart[floors + floorsBelowGround + 1];

        connectionAtX = new boolean[floors + floorsBelowGround + 1];
        connectionAtZ = new boolean[floors + floorsBelowGround + 1];
        Building building = (Building) getBuilding();
        for (int i = 0; i <= floors + floorsBelowGround; i++) {
            ConditionContext conditionContext = new ConditionContext(cityLevel + i - floorsBelowGround, i - floorsBelowGround, floorsBelowGround, floors, "<none>", building.getName(),
                    chunkX, chunkZ) {
                @Override
                public boolean isBuilding() {
                    return true;
                }

                @Override
                public boolean isSphere() {
                    return CitySphere.isInSphere(chunkX, chunkZ, new BlockPos(chunkX * 16 + 8, 0, chunkZ * 16 + 8), provider);
                }

                @Override
                public ResourceLocation getBiome() {
                    return provider.getWorld().getBiome(new BlockPos(chunkX * 16 + 8, 0, chunkZ * 16 + 8)).getRegistryName();
                }
            };
            String randomPart = building.getRandomPart(rand, conditionContext);
            floorTypes[i] = Validate.notNull(AssetRegistries.PARTS.get(randomPart), "Null part for " + randomPart);
            randomPart = building.getRandomPart2(rand, conditionContext);
            floorTypes2[i] = AssetRegistries.PARTS.get(randomPart);
            connectionAtX[i] = isCity(chunkX - 1, chunkZ, provider) && (rand.nextFloat() < profile.BUILDING_DOORWAYCHANCE);
            connectionAtZ[i] = isCity(chunkX, chunkZ - 1, provider) && (rand.nextFloat() < profile.BUILDING_DOORWAYCHANCE);
        }

        if (hasBuilding && floorsBelowGround > 0) {
            xRailCorridor = false;
            zRailCorridor = false;
        } else {
            xRailCorridor = rand.nextFloat() < profile.CORRIDOR_CHANCE;
            zRailCorridor = rand.nextFloat() < profile.CORRIDOR_CHANCE;
        }

        if (isCity) {
            xBridge = false;
            zBridge = false;
        } else {
            xBridge = rand.nextFloat() < profile.BRIDGE_CHANCE;
            zBridge = rand.nextFloat() < profile.BRIDGE_CHANCE;
        }

        if (rand.nextFloat() < profile.RAILWAY_DUNGEON_CHANCE) {
            if (!hasBuilding || (Railway.RAILWAY_LEVEL_OFFSET < (cityLevel - floorsBelowGround))) {
                railDungeon = AssetRegistries.PARTS.get(getCityStyle().getRandomRailDungeon(rand));
            } else {
                railDungeon = null;
            }
        } else {
            railDungeon = null;
        }

        if (rand.nextFloat() < profile.BUILDING_FRONTCHANCE) {
            frontType = AssetRegistries.PARTS.get(getCityStyle().getRandomFront(rand));
        } else {
            frontType = null;
        }
    }

    public boolean hasHorizontalMonorail() {
        if (horizontalMonorail == null) {
            horizontalMonorail = CitySphere.hasHorizontalMonorail(chunkX, chunkZ, provider);
        }
        return horizontalMonorail;
    }

    public boolean hasVerticalMonorail() {
        if (verticalMonorail == null) {
            verticalMonorail = CitySphere.hasVerticalMonorail(chunkX, chunkZ, provider);
        }
        return verticalMonorail;
    }

    public boolean hasMonorail() {
        return hasHorizontalMonorail() || hasVerticalMonorail();
    }

    private int getMaxcellars(CityStyle cs) {
        int maxcellars = profile.BUILDING_MAXCELLARS + cityLevel;
        if (buildingType.getMaxCellars() != -1) {
            maxcellars = Math.min(maxcellars, buildingType.getMaxCellars());
        }
        if (buildingType.getMinCellars() != -1) {
            maxcellars = Math.max(maxcellars, buildingType.getMinCellars());
        }
        if (cs.getMaxCellarCount() != null) {
            maxcellars = Math.min(maxcellars, cs.getMaxCellarCount());
        }
        if (cs.getMinCellarCount() != null) {
            maxcellars = Math.max(maxcellars, cs.getMinCellarCount());
        }
        return maxcellars;
    }

    private int getMinfloors(IDimensionInfo provider, CityStyle cs) {
        int minfloors = profile.BUILDING_MINFLOORS + 1;    // +1 because this doesn't count the top
        if (buildingType.getMinFloors() != -1) {
            minfloors = Math.max(minfloors, buildingType.getMinFloors());
        }
        if (cs.getMinFloorCount() != null) {
            minfloors = Math.max(minfloors, cs.getMinFloorCount());
        }
        return minfloors;
    }

    private int getMaxfloors(IDimensionInfo provider, CityStyle cs) {
        int maxfloors = profile.BUILDING_MAXFLOORS;
        if (buildingType.getMaxFloors() != -1) {
            maxfloors = Math.min(maxfloors, buildingType.getMaxFloors());
        }
        if (cs.getMaxFloorCount() != null) {
            maxfloors = Math.min(maxfloors, cs.getMaxFloorCount());
        }
        return maxfloors;
    }

    public int getHighwayXLevel() {
        return Highway.getXHighwayLevel(chunkX, chunkZ, provider, profile);
    }

    public int getHighwayZLevel() {
        return Highway.getZHighwayLevel(chunkX, chunkZ, provider, profile);
    }


    /**
     * Return true if this is a void chunk (only for floating island worldtype). This does
     * not use the cache so it is safe to use when the cache is building
     */
    public static boolean isVoidChunk(int chunkX, int chunkZ, IDimensionInfo provider) {
//        if (provider.otherGenerator != null) {
//            return false;   // @todo Not supported yet
        if (provider.getProfile().isFloating()) {
            if (provider.getHeightmap(chunkX, chunkZ).getHeight(8, 8) <= 0) {
                return true;
            }
            if (provider.getHeightmap(chunkX, chunkZ).getHeight(3, 3) <= 0) {
                return true;
            }
            if (provider.getHeightmap(chunkX, chunkZ).getHeight(12, 3) <= 0) {
                return true;
            }
            if (provider.getHeightmap(chunkX, chunkZ).getHeight(3, 12) <= 0) {
                return true;
            }
            return provider.getHeightmap(chunkX, chunkZ).getHeight(12, 12) <= 0;
        } else {
            return false;
        }
    }


    /**
     * This function does not use the cache. So safe to use when the cache is building
     */
    public static int getCityLevel(int chunkX, int chunkZ, IDimensionInfo provider) {
        if (provider.getProfile().isSpace()) {
            return getCityLevelSpace(chunkX, chunkZ, provider);
        } else if (provider.getProfile().isFloating()) {
            return getCityLevelFloating(chunkX, chunkZ, provider);
        } else if (provider.getProfile().isCavern()) {
            return getCityLevelCavern(chunkX, chunkZ, provider);
        } else {
            return getCityLevelNormal(chunkX, chunkZ, provider, provider.getProfile());
        }
    }

    private static int getCityLevelCavern(int chunkX, int chunkZ, IDimensionInfo provider) {
        // @todo for now
        return getCityLevelFloating(chunkX, chunkZ, provider);
    }



    private static int getCityLevelSpace(int chunkX, int chunkZ, IDimensionInfo provider) {
        if (CitySphere.intersectsWithCitySphere(chunkX, chunkZ, provider)) {
            // In the sphere
            float dist = CitySphere.getRelativeDistanceToCityCenter(chunkX, chunkZ, provider);
            Random rand = new Random(provider.getSeed() + chunkZ * 817505771L + chunkX * 217645177L);
            rand.nextFloat();
            rand.nextFloat();
            if (dist < .3f) {
                return 2 + rand.nextInt(2);
            } else if (dist < .4f) {
                return 1 + rand.nextInt(2);
            } else if (dist < .6f) {
                return rand.nextInt(2);
            } else {
                return 0;
            }
        } else {
            return getCityLevelNormal(chunkX, chunkZ, provider, provider.getOutsideProfile());
        }
    }

    private static int getCityLevelNormal(int chunkX, int chunkZ, IDimensionInfo provider, LostCityProfile profile) {
        // OLD METHOD:

        ChunkHeightmap heightmap = provider.getHeightmap(chunkX, chunkZ);
        int height = heightmap.getAverageHeight();
        return getLevelBasedOnHeight(height, profile);
    }

    private static int getCityLevelFloating(int chunkX, int chunkZ, IDimensionInfo provider) {
        int cnt = 0;
        int h = 0;
        int h0 = provider.getHeightmap(chunkX, chunkZ).getHeight(8, 8);
        if (h0 > 1) {
            h += h0;
            cnt++;
        }
        int h1 = provider.getHeightmap(chunkX, chunkZ).getHeight(3, 3);
        if (h1 > 1) {
            h += h1;
            cnt++;
        }
        int h2 = provider.getHeightmap(chunkX, chunkZ).getHeight(12, 3);
        if (h2 > 1) {
            h += h2;
            cnt++;
        }
        int h3 = provider.getHeightmap(chunkX, chunkZ).getHeight(3, 12);
        if (h3 > 1) {
            h += h3;
            cnt++;
        }
        int h4 = provider.getHeightmap(chunkX, chunkZ).getHeight(12, 12);
        if (h4 > 1) {
            h += h4;
            cnt++;
        }
        if (cnt > 0) {
            h = h / cnt;
        }
        return getLevelBasedOnHeight(h, provider.getProfile());
    }

    private static int getLevelBasedOnHeight(int height, LostCityProfile profile) {
        if (height < profile.CITY_LEVEL0_HEIGHT) {
            return 0;
        } else if (height < profile.CITY_LEVEL1_HEIGHT) {
            return 1;
        } else if (height < profile.CITY_LEVEL2_HEIGHT) {
            return 2;
        } else if (height < profile.CITY_LEVEL3_HEIGHT) {
            return 3;
        } else {
            return 4;
        }
    }

    private Block getRandomDoor(Random rand) {
        Block doorBlock;
        switch (rand.nextInt(7)) {
            case 0:
                doorBlock = Blocks.BIRCH_DOOR;
                break;
            case 1:
                doorBlock = Blocks.ACACIA_DOOR;
                break;
            case 2:
                doorBlock = Blocks.DARK_OAK_DOOR;
                break;
            case 3:
                doorBlock = Blocks.SPRUCE_DOOR;
                break;
            case 5:
                doorBlock = Blocks.JUNGLE_DOOR;
                break;
            case 6:
                doorBlock = Blocks.IRON_DOOR;
                break;
            case 4:
            default:
                doorBlock = Blocks.OAK_DOOR;
        }
        return doorBlock;
    }

    public boolean isStreetSection() {
        return isCity && !hasBuilding;
    }

    public boolean isElevatedParkSection() {
        if (!isStreetSection()) {
            return false;
        }
        if (!getXmin().isStreetSection()) {
            return false;
        }
        if (!getXmax().isStreetSection()) {
            return false;
        }
        if (!getZmin().isStreetSection()) {
            return false;
        }
        if (!getZmax().isStreetSection()) {
            return false;
        }
        int cnt = 0;
        cnt += getXmin().getZmin().isStreetSection() ? 1 : 0;
        cnt += getXmin().getZmax().isStreetSection() ? 1 : 0;
        cnt += getXmax().getZmin().isStreetSection() ? 1 : 0;
        cnt += getXmax().getZmax().isStreetSection() ? 1 : 0;
        return cnt >= 3;
    }

    private Direction getStairDirection() {
        if (!stairsCalculated) {
            stairsCalculated = true;
            if (streetType != StreetType.PARK && !hasBuilding && isCity) {
                if (cityLevel == getXmin().cityLevel - 1 && !getXmin().hasBuilding && getXmin().isCity) {
                    stairDirection = Direction.XMIN;
                } else if (cityLevel == getXmax().cityLevel - 1 && !getXmax().hasBuilding && getXmax().isCity) {
                    stairDirection = Direction.XMAX;
                } else if (cityLevel == getZmin().cityLevel - 1 && !getZmin().hasBuilding && getZmin().isCity) {
                    stairDirection = Direction.ZMIN;
                } else if (cityLevel == getZmax().cityLevel - 1 && !getZmax().hasBuilding && getZmax().isCity) {
                    stairDirection = Direction.ZMAX;
                } else {
                    stairDirection = null;
                }
            } else {
                stairDirection = null;
            }
        }
        return stairDirection;
    }

    // This returns the actual stair direction. It keeps track if there are stair chunks around
    // it those have higher stair priority
    public Direction getActualStairDirection() {
        if (!actualStairsCalculated) {
            actualStairsCalculated = true;
            actualStairDirection = getStairDirection();
            if (actualStairDirection != null) {
                for (int cx = -1; cx <= 1; cx++) {
                    for (int cz = -1; cz <= 1; cz++) {
                        if (cx != 0 || cz != 0) {
                            BuildingInfo adjacent = getBuildingInfo(chunkX + cx, chunkZ + cz, provider);
                            if (adjacent.getStairDirection() != null && adjacent.stairPriority > stairPriority) {
                                actualStairDirection = null;
                                break;
                            }
                        }
                    }
                }
            }
        }
        return actualStairDirection;
    }


    public BuildingPart hasBridge(IDimensionInfo provider, Orientation orientation) {
        switch (orientation) {
            case X:
                return hasXBridge(provider);
            case Z:
                return hasZBridge(provider);
        }
        return null;
    }

    // To prevent adjacent bridges of the same direction we give the bridges at even chunk Z coordinates higher priority
    public BuildingPart hasXBridge(IDimensionInfo provider) {
        if (xBridgeTypeCalculated) {
            return xBridgeType;
        }
        xBridgeTypeCalculated = true;
        xBridgeType = null;

        if (!xBridge) {
            return null;
        }
        if (!isSuitableForBridge(provider, this)) {
            return null;
        }
        if (chunkZ % 2 != 0 && (getZmin().hasXBridge(provider) != null || getZmax().hasXBridge(provider) != null)) {
            return null;
        }
        BuildingPart bt = bridgeType;
        BuildingInfo i = getXmin();
        while ((!i.isCity) && i.xBridge && isSuitableForBridge(provider, i)) {
            if (chunkZ % 2 != 0 && (i.getZmin().hasXBridge(provider) != null || i.getZmax().hasXBridge(provider) != null)) {
                return null;
            }
            bt = i.bridgeType;
            i = i.getXmin();
        }
        if ((!i.isCity) || i.hasBuilding || i.cityLevel > 0) {  // @todo support bridges at higher levels?
            return null;
        }

        BuildingInfo minimum = i;

        i = getXmax();
        while ((!i.isCity) && i.xBridge && isSuitableForBridge(provider, i)) {
            if (chunkZ % 2 != 0 && (i.getZmin().hasXBridge(provider) != null || i.getZmax().hasXBridge(provider) != null)) {
                return null;
            }
            i = i.getXmax();
        }
        if ((!i.isCity) || i.hasBuilding || i.cityLevel > 0) {
            return null;
        }
        xBridgeType = bt;
        // Here we can automatically mark the rest of the bridge as ok. Saves on calculation
        i = i.getXmin();
        while (i != minimum) {
            i.xBridgeType = bt;
            i.xBridgeTypeCalculated = true;
            i.zBridgeType = null;
            i.zBridgeTypeCalculated = true;
            i = i.getXmin();
        }

        return bt;
    }

    // To prevent adjacent bridges of the same direction we give the bridges at even chunk X coordinates higher priority
    public BuildingPart hasZBridge(IDimensionInfo provider) {
        if (zBridgeTypeCalculated) {
            return zBridgeType;
        }
        zBridgeTypeCalculated = true;
        zBridgeType = null;

        if (!zBridge) {
            return null;
        }
        if (!isSuitableForBridge(provider, this)) {
            return null;
        }
        if (hasXBridge(provider) != null) {
            return null;
        }

        if (chunkX % 2 != 0 && (getXmin().hasZBridge(provider) != null || getXmax().hasZBridge(provider) != null)) {
            return null;
        }

        BuildingPart bt = bridgeType;
        BuildingInfo i = getZmin();
        while ((!i.isCity) && i.zBridge && isSuitableForBridge(provider, i)) {
            if (i.hasXBridge(provider) != null) {
                return null;
            }
            if (chunkX % 2 != 0 && (i.getXmin().hasZBridge(provider) != null || i.getXmax().hasZBridge(provider) != null)) {
                return null;
            }

            bt = i.bridgeType;
            i = i.getZmin();
        }

        BuildingInfo minimum = i;

        if ((!i.isCity) || i.hasBuilding || i.cityLevel > 0) {
            return null;
        }
        i = getZmax();
        while ((!i.isCity) && i.zBridge && isSuitableForBridge(provider, i)) {
            if (i.hasXBridge(provider) != null) {
                return null;
            }
            if (chunkX % 2 != 0 && (i.getXmin().hasZBridge(provider) != null || i.getXmax().hasZBridge(provider) != null)) {
                return null;
            }
            i = i.getZmax();
        }
        if ((!i.isCity) || i.hasBuilding || i.cityLevel > 0) {
            return null;
        }
        zBridgeType = bt;
        // Here we can automatically mark the rest of the bridge as ok. Saves on calculation
        i = i.getZmin();
        while (i != minimum) {
            i.zBridgeType = bt;
            i.zBridgeTypeCalculated = true;
            i.xBridgeType = null;
            i.xBridgeTypeCalculated = true;
            i = i.getZmin();
        }

        return bt;
    }

    public boolean isOcean() {
        if (isOcean != null) {
            return isOcean;
        }
        isOcean = BiomeInfo.getBiomeInfo(provider, new ChunkCoord(provider.getType(), chunkX, chunkZ)).getMainBiome().getBiomeCategory() == Biome.Category.OCEAN;
        return isOcean;
    }

    private static boolean isOcean(Biome[] biomes) {
        int cnt = 0;
        for (Biome biome : biomes) {
            Biome.Category category = biome.getBiomeCategory();
            if (category == Biome.Category.OCEAN) {
                cnt++;
            }
        }
        return (cnt * 100 / biomes.length) > 50;
    }


    private boolean isSuitableForBridge(IDimensionInfo provider, BuildingInfo i) {
        if (provider.getProfile().isSpace() && hasMonorail()) {
            return false;
        }
        return i.cityLevel < cityLevel || LostCityTerrainFeature.isWaterBiome(provider, i.coord);
    }


    public boolean hasXCorridor() {
        if (!xRailCorridor) {
            return false;
        }
        BuildingInfo i = getXmin();
        while (i.canRailGoThrough() && i.xRailCorridor) {
            i = i.getXmin();
        }
        if ((!i.hasBuilding) || i.floorsBelowGround == 0) {
            return false;
        }
        i = getXmax();
        while (i.canRailGoThrough() && i.xRailCorridor) {
            i = i.getXmax();
        }
        return !((!i.hasBuilding) || i.floorsBelowGround == 0);
    }

    public boolean hasZCorridor() {
        if (!zRailCorridor) {
            return false;
        }
        BuildingInfo i = getZmin();
        while (i.canRailGoThrough() && i.zRailCorridor) {
            i = i.getZmin();
        }
        if ((!i.hasBuilding) || i.floorsBelowGround == 0) {
            return false;
        }
        i = getZmax();
        while (i.canRailGoThrough() && i.zRailCorridor) {
            i = i.getZmax();
        }
        return !((!i.hasBuilding) || i.floorsBelowGround == 0);
    }

    // Return true if it is possible for a rail section to go through here
    public boolean canRailGoThrough() {
        if (!isCity) {
            // There is no city here so no passing possible
            return false;
        }
        if (!hasBuilding) {
            // There is no building here but we have a city so we can pass
            return true;
        }
        // Otherwise we can only pass if this building has no floors below ground
        return floorsBelowGround == 0;
    }

    // Return true if it is possible for a water corridor to go through here
    public boolean canWaterCorridorGoThrough() {
        if (!isCity) {
            // There is no city here so no passing possible
            return false;
        }
        if (!hasBuilding) {
            // There is no building here but we have a city so we can pass
            return true;
        }
        // Otherwise we can only pass if this building has at most one floor below ground
        return floorsBelowGround <= 1;
    }

    // Return true if the road from a neighbouring chunk can extend into this chunk
    public boolean doesRoadExtendTo() {
        boolean b = isCity && !hasBuilding;
        if (b) {
            return isElevatedParkSection();
        }
        return true;
    }

    // Return true if there can be a road connection between the two given chunks
    public static boolean hasRoadConnection(BuildingInfo i1, BuildingInfo i2) {
        if (i1.doesRoadExtendTo()) {
            return false;
        }
        if (i2.doesRoadExtendTo()) {
            return false;
        }
        /* @todo temporary, should be <= 1 */
        // We allow a road difference of 1 maximum
        return Math.abs(i1.cityLevel - i2.cityLevel) <= 0;
    }

    public static Random getBuildingRandom(int chunkX, int chunkZ, long seed) {
        Random rand = new QualityRandom(seed + chunkZ * 341873128712L + chunkX * 132897987541L);
        rand.nextFloat();
        rand.nextFloat();
        return rand;
    }

    // Convert a local building level to a global one (where cityLevel == 0)
    public int localToGlobal(int l) {
        return l + cityLevel;
    }

    public int globalToLocal(int l) {
        return l - cityLevel;
    }

    public boolean hasConnectionAt(int level, Orientation orientation) {
        switch (orientation) {
            case X:
                return hasConnectionAtX(level);
            case Z:
                return hasConnectionAtZ(level);
        }
        throw new IllegalStateException("Cannot happen!");
    }

    // Call this from the street reference with the (potential building) as 'adj'
    // 'streetLevel' is the cityLevel at the position of the street
    public boolean hasFrontPartFrom(BuildingInfo adj) {
        BuildingInfo.StreetType st = streetType;
        boolean elevated = isElevatedParkSection();
        if (elevated) {
            st = BuildingInfo.StreetType.PARK;
        }

        if (adj.hasBuilding && adj.frontType != null && st == BuildingInfo.StreetType.NORMAL && cityLevel < adj.cityLevel + adj.getNumFloors()) {
            RailChunkType type = getRailInfo().getType();
            if (type == RailChunkType.STATION_UNDERGROUND) {
                return false;
            }
            if (type == RailChunkType.GOING_DOWN_ONE_FROM_SURFACE) {
                return false;
            }
            if (getMaxHighwayLevel() >= 0) {
                return false;
            }

            int local = adj.globalToLocal(cityLevel);
            return !adj.isValidFloor(local) || !adj.getFloor(local).getMetaBoolean("dontconnect");
        } else {
            return false;
        }
    }


    // This checks if there can be a connection at minX
    public boolean hasConnectionAtX(int level) {
        if (!isCity) {
            return false;
        }
        if (building2x2Section == 1 || building2x2Section == 3) {
            return false;
        }
        if (level < 0 || level >= connectionAtX.length) {
            return false;
        }
        if (level < floorTypes.length && floorTypes[level].getMetaBoolean("dontconnect")) {
            return false;       // No connection supported
        }
        if (getXmin().hasFrontPartFrom(this)) {
            return true;
        }
        return connectionAtX[level];
    }

    // This checks if there can be a connection at minX
    public boolean hasConnectionAtXFromStreet(int level) {
        if (!isCity) {
            return false;
        }
        if (building2x2Section == 1 || building2x2Section == 3) {
            return false;
        }
        if (level < 0 || level >= connectionAtX.length) {
            return false;
        }
        if (hasFrontPartFrom(getXmin())) {
            return true;
        }
        return connectionAtX[level];
    }

    // This checks if there can be a connection at minZ
    public boolean hasConnectionAtZ(int level) {
        if (!isCity) {
            return false;
        }
        if (building2x2Section == 2 || building2x2Section == 3) {
            return false;
        }
        if (level < 0 || level >= connectionAtZ.length) {
            return false;
        }
        if (level < floorTypes.length && floorTypes[level].getMetaBoolean("dontconnect")) {
            return false;       // No connection supported
        }
        if (getZmin().hasFrontPartFrom(this)) {
            return true;
        }
        return connectionAtZ[level];
    }

    // This checks if there can be a connection at minZ
    public boolean hasConnectionAtZFromStreet(int level) {
        if (!isCity) {
            return false;
        }
        if (building2x2Section == 2 || building2x2Section == 3) {
            return false;
        }
        if (level < 0 || level >= connectionAtZ.length) {
            return false;
        }
        if (hasFrontPartFrom(getZmin())) {
            return true;
        }
        return connectionAtZ[level];
    }

    /**
     * Get the lowest height of a corner of four chunks (if it is a city chunk).
     * info: reference to the bottom-right chunk. The 0,0 position of this chunk is the reference.
     * Returns 100000 if the corner is not adjacent to any city chunk
     * Also returns 100000 if all corners are city or landscape chunks (as
     * this kind of corner should also have no effect on the landscape beyond those chunks)
     * This is the level 0 version which looks at current chunk corner only
     */
    public int getLowestCityHeightAtChunkCorner() {
        BuildingInfo info00 = getXmin().getZmin();
        BuildingInfo info01 = getXmin();
        BuildingInfo info10 = getZmin();
        if (isCity && info10.isCity && info00.isCity && info01.isCity) {
            return 100000;
        }
        if (!isCity && !info10.isCity && !info00.isCity && !info01.isCity) {
            return 100000;
        }
        // If we come here we have a mix of city and normal chunks
        int h = getCityHeightForChunk();
        h = Math.min(h, info01.getCityHeightForChunk());
        h = Math.min(h, info10.getCityHeightForChunk());
        h = Math.min(h, info00.getCityHeightForChunk());
        return h;
    }

    /*
     * This is used for correcting the terrain and indicates the desired
     * level to which adjacent terrains should interpolate
     */
    public int getCityHeightForChunk() {
        if (isCity) {
            return getCityGroundLevel();
        } else {
            if (isOcean()) {    // @todo check?
                return groundLevel - 4;
            } else {
                return 100000;
            }
        }
    }

    /**
     * Given adjacent (city) chunks, calculate the desired height to interpolate the
     * landscape to (minimum/maximum). This is calculated for the reference position of this chunk (0,0 point)
     * This is the level 1 version which looks at adjacent heights only
     */
    private Integer[] getDesiredMaxHeightL1() {
        if (desiredMaxHeight1 == null) {
            int h = getLowestCityHeightAtChunkCorner();

            int cx = chunkX;
            int cz = chunkZ;

            if (h < 256) {
                // The L0 height at this corner is fixed so we return that
                desiredMaxHeight1 = new Integer[]{
                        h + LostCityTerrainFeature.getRandomizedOffset(cx, cz, profile.TERRAIN_FIX_LOWER_MIN_OFFSET, profile.TERRAIN_FIX_LOWER_MAX_OFFSET),
                        h + LostCityTerrainFeature.getRandomizedOffset(cx, cz, profile.TERRAIN_FIX_UPPER_MIN_OFFSET, profile.TERRAIN_FIX_UPPER_MAX_OFFSET)};
                return desiredMaxHeight1;
            }

            Integer[] minMax = new Integer[]{100000, 100000};

            getXmin().getZmin().updateMinMaxL1(minMax, 25 + LostCityTerrainFeature.getHeightOffsetL1(cx - 1, cz - 1));
            getXmin().updateMinMaxL1(minMax, 20 + LostCityTerrainFeature.getHeightOffsetL1(cx - 1, cz));
            getXmin().getZmax().updateMinMaxL1(minMax, 25 + LostCityTerrainFeature.getHeightOffsetL1(cx - 1, cz + 1));

            getZmin().updateMinMaxL1(minMax, 20 + LostCityTerrainFeature.getHeightOffsetL1(cx, cz - 1));
            getZmax().updateMinMaxL1(minMax, 20 + LostCityTerrainFeature.getHeightOffsetL1(cx, cz + 1));

            getXmax().getZmin().updateMinMaxL1(minMax, 25 + LostCityTerrainFeature.getHeightOffsetL1(cx + 1, cz - 1));
            getXmax().updateMinMaxL1(minMax, 20 + LostCityTerrainFeature.getHeightOffsetL1(cx + 1, cz));
            getXmax().getZmax().updateMinMaxL1(minMax, 25 + LostCityTerrainFeature.getHeightOffsetL1(cx + 1, cz + 1));

            desiredMaxHeight1 = minMax;
        }
        return desiredMaxHeight1;
    }

    /**
     * Given adjacent (city) chunks, calculate the desired height to interpolate the
     * landscape too. This is calculated for the reference position of this chunk (0,0 point)
     * This is the level 2 version which looks at L1 heights of adjacent chunks
     */
    public Integer[] getDesiredMaxHeightL2() {
        if (desiredTerrainCorrectionHeights == null) {
            Integer[] mm = getDesiredMaxHeightL1();
            if (mm[0] < 256) {
                // The L1 height at this corner is fixed so we return that
                desiredTerrainCorrectionHeights = new Integer[] { mm[0], mm[1] };
                return desiredTerrainCorrectionHeights;
            }

            int cx = chunkX;
            int cz = chunkZ;

            Integer[] minMax = new Integer[]{100000, 100000};

            getXmin().getZmin().updateMinMaxL2(minMax, 25 + LostCityTerrainFeature.getHeightOffsetL2(cx - 1, cz - 1));
            getXmin().updateMinMaxL2(minMax, 20 + LostCityTerrainFeature.getHeightOffsetL2(cx - 1, cz));
            getXmin().getZmax().updateMinMaxL2(minMax, 25 + LostCityTerrainFeature.getHeightOffsetL2(cx - 1, cz + 1));

            getZmin().updateMinMaxL2(minMax, 20 + LostCityTerrainFeature.getHeightOffsetL2(cx, cz - 1));
            getZmax().updateMinMaxL2(minMax, 20 + LostCityTerrainFeature.getHeightOffsetL2(cx, cz + 1));

            getXmax().getZmin().updateMinMaxL2(minMax, 25 + LostCityTerrainFeature.getHeightOffsetL2(cx + 1, cz - 1));
            getXmax().updateMinMaxL2(minMax, 20 + LostCityTerrainFeature.getHeightOffsetL2(cx + 1, cz));
            getXmax().getZmax().updateMinMaxL2(minMax, 25 + LostCityTerrainFeature.getHeightOffsetL2(cx + 1, cz + 1));
            desiredTerrainCorrectionHeights = minMax;
        }
        return desiredTerrainCorrectionHeights;
    }

    public void updateMinMaxL2(Integer[] minMax, int offs) {
        Integer[] h = getDesiredMaxHeightL1();
        if ((h[0]-offs) < minMax[0]) {
            minMax[0] = h[0]-offs;
        }
        if ((h[1]+offs) < minMax[1]) {
            minMax[1] = h[1]+offs;
        }
    }


    private void updateMinMaxL1(Integer[] minMax, int offs) {
        int h = getLowestCityHeightAtChunkCorner();
        if ((h-offs) < minMax[0]) {
            minMax[0] = h-offs;
        }
        if ((h+offs) < minMax[1]) {
            minMax[1] = h+offs;
        }
    }


    public enum StreetType {
        NORMAL,
        FULL,
        PARK
    }


    @Override
    public boolean isCity() {
        return this.isCity;
    }

    @Override
    public String getBuildingType() {
        return hasBuilding ? buildingType.getName() : null;
    }

    @Override
    public int getCityLevel() {
        return cityLevel;
    }

    @Override
    public int getNumFloors() {
        return floors;
    }

    @Override
    public int getNumCellars() {
        return floorsBelowGround;
    }

    @Override
    public float getDamage(int chunkY) {
        return getDamageArea().getDamage(chunkX * 16 + 8, chunkY * 16 + 8, chunkZ * 16 + 8);
    }

    @Override
    public Collection<ILostExplosion> getExplosions() {
        return new ArrayList<>(getDamageArea().getExplosions());
    }

    @Override
    public int getMaxHighwayLevel() {
        return Math.max(getHighwayXLevel(), getHighwayZLevel());
    }

    @Nonnull
    @Override
    public RailChunkType getRailType() {
        return getRailInfo().getType();
    }

    @Override
    public int getRailLevel() {
        return getRailInfo().getLevel();
    }

    @Nullable
    @Override
    public ILostCityInfo getCityInfo() {
        if (City.isCityCenter(chunkX, chunkZ, provider)) {
            return new ILostCityInfo() {
                @Override
                public float getCityRadius() {
                    return City.getCityRadius(chunkX, chunkZ, provider);
                }

                @Override
                public String getCityStyle() {
                    return City.getCityStyleForCityCenter(chunkX, chunkZ, provider);
                }
            };
        } else {
            return null;
        }
    }

    @Override
    public int getRuinLevel() {
        if (profile.RUIN_CHANCE <= 0.0) {
            return -1;
        }
        if (ruinHeight < 0) {
            return -1;
        }
        return (int) (getCityGroundLevel() + 1 + (ruinHeight * getNumFloors() * 6.0f));
    }

    @Nullable
    @Override
    public ILostSphere getSphere() {
        CitySphere sphere = CitySphere.getCitySphere(chunkX, chunkZ, provider);
        if (sphere.isEnabled()) {
            return sphere;
        }
        return null;
    }
}
