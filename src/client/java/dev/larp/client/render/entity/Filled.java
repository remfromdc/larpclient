package dev.larp.client.render.entity;

public interface Filled {
    EntityFill larp$fill();

    void larp$setFill(EntityFill fill);

    EntityOutline larp$outline();

    void larp$setOutline(EntityOutline outline);
}
