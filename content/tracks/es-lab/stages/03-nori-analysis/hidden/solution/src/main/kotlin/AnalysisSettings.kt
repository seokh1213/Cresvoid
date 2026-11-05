fun catalogAnalysis(): String = """{
  "tokenizer": {
    "catalog_tokenizer": {
      "type": "nori_tokenizer",
      "decompound_mode": "mixed",
      "user_dictionary_rules": ["초경량패딩 초경량 패딩", "초경량", "노바핏"]
    }
  },
  "filter": {
    "catalog_pos": {"type": "nori_part_of_speech", "stoptags": ["JKS", "JKC", "JKG", "JKO", "JKB", "JKV", "JKQ", "JX", "JC", "EP", "EF", "EC", "ETN", "ETM"]}
  },
  "analyzer": {
    "catalog": {
      "type": "custom",
      "tokenizer": "catalog_tokenizer",
      "filter": ["catalog_pos", "lowercase"]
    }
  }
}
""".trimIndent()
