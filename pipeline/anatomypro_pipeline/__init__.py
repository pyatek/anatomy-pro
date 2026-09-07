"""Converts the Z-Anatomy Blender atlas into Anatomy Pro content packs.

Everything here except `blender_export` is pure Python with no `bpy` import, so the
rules that decide identifiers can be tested without Blender installed. That split is
deliberate: `StructureId`s are permanent (design spec §5), and the naming rules are
where mistakes would be both easy and irreversible.
"""
