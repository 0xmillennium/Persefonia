def runtime_descriptors:
  .manifests[]
  | select(.platform.os != "unknown" or .platform.architecture != "unknown");

def valid_runtime_descriptor:
  (.platform.os | type) == "string"
  and (.platform.architecture | type) == "string"
  and (.digest | type) == "string"
  and (.digest | test("^sha256:[a-f0-9]{64}$"))
  and (.mediaType == "application/vnd.oci.image.manifest.v1+json"
       or .mediaType == "application/vnd.docker.distribution.manifest.v2+json");

def runtime_platform:
  "\(.platform.os)/\(.platform.architecture)";

if (.manifests | type) != "array" then
  false
else
  [runtime_descriptors |
    if valid_runtime_descriptor then runtime_platform
    else error("Invalid runtime manifest descriptor") end
  ] as $actual
  | ($actual | length) == ($expected | length)
    and ($actual | sort) == ($expected | sort)
end
