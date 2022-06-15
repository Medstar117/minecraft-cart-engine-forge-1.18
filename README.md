# Audaki Cart Engine

## Brief
Audaki Cart Engine offers well-designed, properly balanced, competitive, and viable cart-based passenger transportation.

This mod is a server-sided (and SSP) mod based originally on Fabric, but has recently been ported to Forge.

## Foundation
This mod was created since every mod I found that increased the speed of minecarts broke stuff and didn't
work with existing rail lines. Additionally, most redstone stuff breaks when your cart moves with more than 10m/s.

## Quality Engineering
This mod is currently a huge overhaul of the minecart movement engine to support higher speeds (up to 34m/s)
while still supporting existing lines with curved, ascending, and descending rail pieces with no problem.

Additionally, redstone rails (like detector and activator rails) still work.

The Cart Engine was tested under a lot of different conditions; but if you find an edge case, it'll be fixed.

## Engine Methodology
The engine travels along the expected path of the rail line and will temporarily slow down to cross important parts
like curves and ascending/descending track pieces. However, the engine is coded in a way that the momentum is mostly restored
after passing the curve/hill or redstone piece in the rail line.

## Game Design / Balancing
The goal for this mod is not just to implement a new Cart Engine of course, but also to provide good gameplay!

To support this goal, a LOT of stuff was tweaked so that the powered rail's gold requirement to reach certain
speeds is well-balanced in a way that actually makes the creation of a high-speed railway a proper end-game goal.

Additionally, the speed is balanced in a way that so that riding the railway is a lot of fun, and is better than packed ice.

Due to the balanced acceleration curve, railways can still be used early-game with lower speeds and with less gold investment

## Features / Balancing
- Fixed a long-standing vanilla bug that doubled the Cart Movement Speed when a block is skipped in a tick
- Raised maximum speed from 8m/s to 34m/s for carts with passengers (achievable when there are 8 straight rail
  pieces behind and in front of the cart)
- Raised fallback maximum speed from 8m/s to 9.2m/s for carts with passengers
- Tweaked powered rail acceleration to factor in fewer spent ticks on higher speeds and to multiply the acceleration accordingly
- Tweaked acceleration to require more powered rails on higher speeds
- Tweaked acceleration to feel good and somewhat train-like.
- Tweaked achievable momentum for the new high-speed
- Tweaked brakes (i.e. unpowered Power-Rail) to handle the higher speed and momentum properly
- Tweaked "kick-start" speed when starting from a standing block with a powered rail
- Cart Engine simulates travel along the railway and dynamically adjusts allowed speed based on rail conditions around the cart
- High-Speed Cart temporarily slows down for slopes and curves
- High-Speed Cart temporarily slows down for Detector Rails and Activator Rails
