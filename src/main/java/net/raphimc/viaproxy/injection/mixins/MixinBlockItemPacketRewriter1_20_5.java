/*
 * This file is part of ViaProxy - https://github.com/RaphiMC/ViaProxy
 * Copyright (C) 2021-2024 RK_01/RaphiMC and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.raphimc.viaproxy.injection.mixins;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.HolderSet;
import com.viaversion.viaversion.api.minecraft.data.StructuredDataContainer;
import com.viaversion.viaversion.api.minecraft.data.StructuredDataKey;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.minecraft.item.data.FoodEffect;
import com.viaversion.viaversion.api.minecraft.item.data.FoodProperties;
import com.viaversion.viaversion.api.minecraft.item.data.ToolProperties;
import com.viaversion.viaversion.api.minecraft.item.data.ToolRule;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.libs.fastutil.ints.IntOpenHashSet;
import com.viaversion.viaversion.libs.fastutil.ints.IntSet;
import com.viaversion.viaversion.libs.gson.JsonArray;
import com.viaversion.viaversion.libs.gson.JsonElement;
import com.viaversion.viaversion.libs.gson.JsonObject;
import com.viaversion.viaversion.protocols.protocol1_20_3to1_20_2.packet.ClientboundPacket1_20_3;
import com.viaversion.viaversion.protocols.protocol1_20_5to1_20_3.Protocol1_20_5To1_20_3;
import com.viaversion.viaversion.protocols.protocol1_20_5to1_20_3.packet.ServerboundPacket1_20_5;
import com.viaversion.viaversion.protocols.protocol1_20_5to1_20_3.rewriter.BlockItemPacketRewriter1_20_5;
import com.viaversion.viaversion.rewriter.ItemRewriter;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import net.raphimc.viaproxy.ViaProxy;
import net.raphimc.viaproxy.plugins.events.ViaLoadingEvent;
import net.raphimc.viaproxy.protocoltranslator.impl.ViaProxyMappingDataLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

@Mixin(value = BlockItemPacketRewriter1_20_5.class, remap = false)
public abstract class MixinBlockItemPacketRewriter1_20_5 extends ItemRewriter<ClientboundPacket1_20_3, ServerboundPacket1_20_5, Protocol1_20_5To1_20_3> {

    @Unique
    private final Set<String> foodItems_b1_7_3 = new HashSet<>();

    @Unique
    private final Map<String, Integer> armorMaxDamage_b1_8_1 = new HashMap<>();

    @Unique
    private final Set<String> swordItems1_8 = new HashSet<>();

    @Unique
    private final Map<ProtocolVersion, Map<String, ToolProperties>> toolDataChanges = new LinkedHashMap<>();

    public MixinBlockItemPacketRewriter1_20_5() {
        super(null, null, null, null, null);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    public void loadItemMappings(Protocol1_20_5To1_20_3 protocol, CallbackInfo ci) {
        this.foodItems_b1_7_3.add("minecraft:apple");
        this.foodItems_b1_7_3.add("minecraft:mushroom_stew");
        this.foodItems_b1_7_3.add("minecraft:bread");
        this.foodItems_b1_7_3.add("minecraft:porkchop");
        this.foodItems_b1_7_3.add("minecraft:cooked_porkchop");
        this.foodItems_b1_7_3.add("minecraft:golden_apple");
        this.foodItems_b1_7_3.add("minecraft:cod");
        this.foodItems_b1_7_3.add("minecraft:cooked_cod");
        this.foodItems_b1_7_3.add("minecraft:cookie");

        final JsonObject armorMaxDamages = ViaProxyMappingDataLoader.INSTANCE.loadData("armor-damages-b1.8.1.json");
        for (Map.Entry<String, JsonElement> entry : armorMaxDamages.entrySet()) {
            final String item = entry.getKey();
            final int maxDamage = entry.getValue().getAsInt();
            this.armorMaxDamage_b1_8_1.put(item, maxDamage);
        }

        this.swordItems1_8.add("minecraft:wooden_sword");
        this.swordItems1_8.add("minecraft:stone_sword");
        this.swordItems1_8.add("minecraft:iron_sword");
        this.swordItems1_8.add("minecraft:golden_sword");
        this.swordItems1_8.add("minecraft:diamond_sword");

        ViaProxy.EVENT_MANAGER.registerRunnable(() -> {
            final JsonObject itemToolComponents = ViaProxyMappingDataLoader.INSTANCE.loadData("item-tool-components.json");
            for (Map.Entry<String, JsonElement> entry : itemToolComponents.entrySet()) {
                final ProtocolVersion version = ProtocolVersion.getClosest(entry.getKey());
                if (version == null) {
                    throw new IllegalStateException("Unknown protocol version: " + entry.getKey());
                }
                final Map<String, ToolProperties> toolProperties = new HashMap<>();
                final JsonArray toolComponents = entry.getValue().getAsJsonArray();
                for (JsonElement toolComponent : toolComponents) {
                    final JsonObject toolComponentObject = toolComponent.getAsJsonObject();
                    final String item = toolComponentObject.get("item").getAsString();
                    final float defaultMiningSpeed = toolComponentObject.get("default_mining_speed").getAsFloat();
                    final int damagePerBlock = toolComponentObject.get("damage_per_block").getAsInt();
                    final int[] suitableFor = this.blockJsonArrayToIds(version, toolComponentObject.getAsJsonArray("suitable_for"));
                    final List<ToolRule> toolRules = new ArrayList<>();
                    final JsonArray miningSpeeds = toolComponentObject.getAsJsonArray("mining_speeds");
                    for (JsonElement miningSpeed : miningSpeeds) {
                        final JsonObject miningSpeedObject = miningSpeed.getAsJsonObject();
                        final int[] blocks = this.blockJsonArrayToIds(version, miningSpeedObject.getAsJsonArray("blocks"));
                        final float speed = miningSpeedObject.get("speed").getAsFloat();
                        toolRules.add(new ToolRule(HolderSet.of(blocks), speed, null));
                    }
                    if (suitableFor.length > 0) {
                        toolRules.add(new ToolRule(HolderSet.of(suitableFor), null, true));
                    }
                    toolProperties.put(item, new ToolProperties(toolRules.toArray(new ToolRule[0]), defaultMiningSpeed, damagePerBlock));
                }
                this.toolDataChanges.put(version, toolProperties);
            }
        }, ViaLoadingEvent.class);
    }

    @Inject(method = "toStructuredItem", at = @At("RETURN"))
    private void appendItemDataFixComponents(UserConnection connection, Item old, CallbackInfoReturnable<Item> cir) {
        final StructuredDataContainer data = cir.getReturnValue().structuredData();
        final String identifier = this.protocol.getMappingData().getFullItemMappings().identifier(cir.getReturnValue().identifier());
        if (connection.getProtocolInfo().serverProtocolVersion().olderThanOrEqualTo(ProtocolVersion.v1_17_1)) {
            if (identifier.equals("minecraft:crossbow")) {
                data.set(StructuredDataKey.MAX_DAMAGE, 326);
            }
        }
        if (connection.getProtocolInfo().serverProtocolVersion().betweenInclusive(LegacyProtocolVersion.b1_8tob1_8_1, ProtocolVersion.v1_8)) {
            if (this.swordItems1_8.contains(identifier)) {
                data.set(StructuredDataKey.FOOD, new FoodProperties(0, 0F, true, 3600, new FoodEffect[0]));
            }
        }
        if (connection.getProtocolInfo().serverProtocolVersion().olderThanOrEqualTo(LegacyProtocolVersion.b1_8tob1_8_1)) {
            if (this.armorMaxDamage_b1_8_1.containsKey(identifier)) {
                data.set(StructuredDataKey.MAX_DAMAGE, this.armorMaxDamage_b1_8_1.get(identifier));
            }
        }
        if (connection.getProtocolInfo().serverProtocolVersion().olderThanOrEqualTo(LegacyProtocolVersion.b1_7tob1_7_3)) {
            if (this.foodItems_b1_7_3.contains(identifier)) {
                data.set(StructuredDataKey.MAX_STACK_SIZE, 1);
                data.addEmpty(StructuredDataKey.FOOD);
            }
        }

        for (Map.Entry<ProtocolVersion, Map<String, ToolProperties>> entry : this.toolDataChanges.entrySet()) {
            if (connection.getProtocolInfo().serverProtocolVersion().olderThanOrEqualTo(entry.getKey())) {
                final ToolProperties toolProperties = entry.getValue().get(identifier);
                if (toolProperties != null) {
                    data.set(StructuredDataKey.TOOL, toolProperties);
                    break;
                }
            }
        }
    }

    @Unique
    private int[] blockJsonArrayToIds(final ProtocolVersion protocolVersion, final JsonArray jsonArray) {
        final IntSet ids = new IntOpenHashSet();
        for (final JsonElement element : jsonArray) {
            final String name = element.getAsString();
            if (name.startsWith("#")) { // Material name
                final String material = name.substring(1);
                for (Map.Entry<String, Map<ProtocolVersion, String>> entry : ViaProxyMappingDataLoader.BLOCK_MATERIALS.entrySet()) {
                    for (Map.Entry<ProtocolVersion, String> materialEntry : entry.getValue().entrySet()) {
                        if (protocolVersion.olderThanOrEqualTo(materialEntry.getKey()) && materialEntry.getValue().equals(material)) {
                            ids.add(this.protocol.getMappingData().blockId(entry.getKey()));
                            break;
                        }
                    }
                }
            } else { // Block name
                ids.add(this.protocol.getMappingData().blockId(element.getAsString()));
            }
        }
        return ids.toIntArray();
    }

}
