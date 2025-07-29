package toniarts.openkeeper.game.effect;

import toniarts.openkeeper.tools.convert.map.ArtResource;

/**
 * Migrated from world.effect.IEffect, adapted for game.effect.
 */
public interface IEffect {
    String getName();
    ArtResource getArtResource();
    float getAirFriction();
    float getElasticity();
    float getMass();
    float getMinSpeedXy();
    float getMaxSpeedXy();
    float getMinSpeedYz();
    float getMaxSpeedYz();
    float getMinScale();
    float getMaxScale();
    int getMinHp();
    int getMaxHp();
}
