def metadata: .platform.os == "unknown" and .platform.architecture == "unknown";
def runtime: .platform.os != "unknown" and .platform.architecture != "unknown";
def valid_descriptor:
  type == "object" and (.platform | type) == "object"
  and (.platform.os | type) == "string" and (.platform.os | length) > 0
  and (.platform.architecture | type) == "string" and (.platform.architecture | length) > 0
  and (metadata or runtime)
  and (.digest | type) == "string"
  and (.digest | test("^sha256:[a-f0-9]{64}$"))
  and (.mediaType == "application/vnd.oci.image.manifest.v1+json"
       or .mediaType == "application/vnd.docker.distribution.manifest.v2+json");

def runtime_platform:
  "\(.platform.os)/\(.platform.architecture)";

if (.manifests | type) != "array" then
  false
else
  if all(.manifests[]; valid_descriptor) then
    [.manifests[] | select(runtime) | runtime_platform] as $actual
    | ($actual | length) == ($expected | length)
      and ($actual | sort) == ($expected | sort)
  else false end
end
