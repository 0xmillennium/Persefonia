# Geographic data

Made with Natural Earth. The bundled `ne_110m_land.geojson` is the land layer
from the Natural Earth vector repository, pinned to file revision
`693f11422f4e08d2da4566b854dda53eb7c39fb3`.

- Source: https://raw.githubusercontent.com/nvkelso/natural-earth-vector/693f11422f4e08d2da4566b854dda53eb7c39fb3/geojson/ne_110m_land.geojson
- SHA-256: `9e0729ee253ca7d7a5c4ae9395fb1902264c5377c52e224d13dd85010e2835d9`
- Size: 138,160 bytes.
- License: public domain, as stated at https://www.naturalearthdata.com/about/terms-of-use/ (verified 12 October 2026).

`frontend/scripts/geography.mjs` classifies independent Fibonacci-sphere candidate
sets against outer polygon rings and holes: 32,000 land candidates and 9,000 water
candidates. The resulting `land-mask.bin` has 41,000 bits packed into 5,125 bytes;
11,846 candidates classify as land. Runtime retains matching candidates only:
9,252 land dots and 6,406 water dots. Separate densities keep water sparse while
preserving continent detail. The coordinates are generated locally from this
source, independently of external reference point datasets. Source hash and derived bytes are
checked by the production generated-asset gate. Generation and checking run entirely
offline against this pinned source. Only the mask is shipped in the built
application; the GeoJSON and reference recordings are not runtime assets.

The 1:110m dataset intentionally simplifies coastlines and small islands.
The city marker uses independent approximate city-center coordinates supplied
in the task, 41.0082° N / 28.9784° E. It denotes Istanbul, not a personal address.
