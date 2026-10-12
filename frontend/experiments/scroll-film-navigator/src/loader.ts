/** Load through the native same-origin image path, then take explicit bitmap ownership.
 * Blob-based ImageBitmap decoding creates an internal blob URL in WebKit that can
 * report access-control errors during document teardown even with handled promises.
 * Keep an already-started decode in its cache slot until settlement; close it there
 * if the request was superseded. The temporary image is never retained in the cache.
 */
export function loadBitmap(url:string,signal:AbortSignal,observe?:(stage:string)=>void):Promise<ImageBitmap> {
  return new Promise((resolve,reject)=>{
    if(signal.aborted){reject(new DOMException("Aborted","AbortError"));return;}
    const image=new Image();let decoding=false;
    const cleanup=()=>{signal.removeEventListener("abort",abort);image.onload=null;image.onerror=null;image.removeAttribute("src");};
    const abort=()=>{if(!decoding){observe?.("image-aborted");cleanup();reject(new DOMException("Aborted","AbortError"));}};
    signal.addEventListener("abort",abort,{once:true});
    image.decoding="async";
    image.onerror=()=>{observe?.("image-failed");cleanup();reject(new Error(`Image unavailable or invalid: ${url}`));};
    image.onload=()=>{
      decoding=true;image.onload=null;image.onerror=null;
      observe?.("image-ready");
      // createImageBitmap accepts the loaded image directly: no temporary blob URL.
      // Abort during this non-abortable operation leaves the slot occupied. FrameCache
      // owns the returned bitmap, including closing an obsolete completion.
      void Promise.resolve().then(()=>{observe?.("bitmap-start");return createImageBitmap(image);}).then(bitmap=>{observe?.("bitmap-ready");cleanup();resolve(bitmap);},error=>{observe?.("bitmap-failed");cleanup();reject(error);});
    };
    observe?.("image-start");image.src=url;
  });
}
