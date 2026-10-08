const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
class Element {
  constructor() { this.children = []; this.listeners = {}; this.checked = false; this.dataset = {}; }
  append(...nodes) { this.children.push(...nodes); }
  replaceChildren(...nodes) { this.children = nodes; }
  addEventListener(name, handler) { this.listeners[name] = handler; }
}
const nodes = new Map();
const document = { getElementById(id) { if (!nodes.has(id)) nodes.set(id, new Element()); return nodes.get(id); }, createElement() { return new Element(); } };
const requests = [];
const selected = {groups:[{groupNumber:1,stages:['A']}]};
const context = vm.createContext({document, console, fetch:async (url, options) => {
  const request = JSON.parse(options.body); requests.push(request);
  return {ok:true,json:async()=>({status:'OK',normalizedConstraints:[],optimalGroupCount:1,totalSolutionCount:2,
    returnedSolutionCount:request.returnAllSolutions?2:1,selectedSolution:selected,allSolutions:request.returnAllSolutions?[selected,selected]:[selected],
    selectionPolicy:{rules:['RESOURCE_COUNT_EARLIER','STAGE_ID']}})};
}});
vm.runInContext(fs.readFileSync('src/main/resources/static/app.js','utf8'), context);
(async()=>{
  assert.equal(document.getElementById('return-all-solutions').checked,false);
  vm.runInContext("addStage({id:'A',name:'Analysis'});",context);
  const submit = document.getElementById('manager-form').listeners.submit;
  await submit({preventDefault(){}});
  assert.equal(requests[0].returnAllSolutions,false);
  assert.equal(requests[0].managerInput.stages[0].id,'A');
  assert.equal(document.getElementById('solutions').children.length,1);
  assert.equal(document.getElementById('all-solutions-section').hidden,true);
  document.getElementById('return-all-solutions').checked=true;
  document.getElementById('return-all-solutions').listeners.change();
  await submit({preventDefault(){}});
  assert.equal(requests[1].returnAllSolutions,true);
  assert.equal(document.getElementById('solutions').children.length,1);
  assert.equal(document.getElementById('all-solutions-section').hidden,false);
  assert.equal(document.getElementById('all-solutions').children.length,2);
  assert.match(document.getElementById('solution-summary').children[4].textContent,/RESOURCE_COUNT_EARLIER.*STAGE_ID/);
  document.getElementById('reset').listeners.click();
  assert.equal(document.getElementById('return-all-solutions').checked,false);
  console.log('Frontend checkbox, request wrapper, selection, all candidates and reset: PASS');
})().catch(error=>{console.error(error);process.exitCode=1;});

