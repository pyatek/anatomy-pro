package com.ptk.anatomypro.core.data

const val SAMPLE_MANIFEST = """
{
  "pack_id": "skeletal-trunk",
  "structures": [
    {
      "structure_id": "1168-clavicula-left", "ta2_id": "1168",
      "english": "Clavicle", "latin": "Clavicula", "definition": "The collarbone.",
      "system": "skeletal-system", "region": "trunk",
      "parent_id": "361-cingulum-pectorale-median", "laterality": "L",
      "is_group": false, "nodes": ["1168__clavicula__L"], "triangles": 900
    },
    {
      "structure_id": "1169-scapula-left", "ta2_id": "1169",
      "english": "Scapula", "latin": "Scapula", "definition": null,
      "system": "skeletal-system", "region": "trunk",
      "parent_id": "361-cingulum-pectorale-median", "laterality": "L",
      "is_group": false, "nodes": ["1169__scapula__L"], "triangles": 900
    },
    {
      "structure_id": "361-cingulum-pectorale-median", "ta2_id": "361",
      "english": "Pectoral girdle", "latin": "Cingulum pectorale", "definition": null,
      "system": "skeletal-system", "region": "trunk",
      "parent_id": null, "laterality": "M",
      "is_group": true, "nodes": [], "triangles": 0
    }
  ]
}
"""
