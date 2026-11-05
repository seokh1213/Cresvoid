# Elasticsearch with the Korean analyzer. BASE comes from LAB_IMAGES so the plugin always matches the pinned version.
ARG BASE
FROM ${BASE}
RUN bin/elasticsearch-plugin install --batch analysis-nori
