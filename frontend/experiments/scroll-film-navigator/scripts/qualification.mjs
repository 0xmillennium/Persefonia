export const projects=['chromium','firefox','chromium-touch','chromium-tablet-touch','webkit','webkit-touch'];

/**
 * @param {any} report
 * @param {number} code
 * @param {{project:string,policy?:string,evidence?:{classification:string,nativeLog:string}|null}} options
 */
export function summarize(report,code,options) {
  const {project,policy='strict',evidence}=options;
  const tests=[];
  const visit=suite=>{for(const spec of suite.specs??[])for(const test of spec.tests??[])tests.push({title:spec.title,status:test.status,results:test.results??[],annotations:test.annotations??[]});for(const child of suite.suites??[])visit(child);};
  for(const suite of report?.suites??[])visit(suite);
  const unrun=t=>t.status==='interrupted'||(t.status==='skipped'&&!t.results.length&&!t.annotations.some(a=>a.type==='skip'));
  const notRun=tests.filter(unrun).length;
  const passed=tests.filter(t=>t.status==='expected').length,skipped=tests.filter(t=>t.status==='skipped'&&!unrun(t)).length;
  const failed=tests.filter(t=>t.status==='unexpected'),errors=[...(report?.errors??[]),...failed.flatMap(t=>t.results.flatMap(r=>r.errors??[]))];
  // Playwright adds a control notice when --max-failures stops a project. It is
  // retained in evidence, but is not an additional application/browser error.
  const substantive=errors.filter(error=>!/^Testing stopped early after \d+ maximum allowed failures\.$/.test((error.message??'').replace(/\u001b\[[0-9;]*m/g,'')));
  const nativeCrash=substantive.length>0&&substantive.every(error=>/page\.(goto|reload): Page crashed/.test(error.message??''));
  const vmException=project.startsWith('webkit')&&policy==='vm-inconclusive'&&evidence?.classification==='INCONCLUSIVE — LINUX VM WEBKIT'&&typeof evidence.nativeLog==='string'&&nativeCrash;
  const complete=!!report&&tests.length>0&&notRun===0;
  return {project,status:code===0&&complete?'PASS':vmException?'INCONCLUSIVE':failed.length?'FAIL':'PARTIAL',blocking:(!complete||code!==0)&&!vmException,exitCode:code,passed,skipped,failed:failed.length,notRun,errors:errors.map(e=>e.message),vmException};
}
