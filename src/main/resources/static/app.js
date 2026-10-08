'use strict';

const byId = id => document.getElementById(id);
const state = { stages: [], logicalDependencies: [], legalDependencies: [] };
let nextKey = 0;

function element(tag, text, className) {
  const node = document.createElement(tag);
  if (text !== undefined) node.textContent = text;
  if (className) node.className = className;
  return node;
}

function clearResults() {
  byId('results').hidden = true;
  byId('error').hidden = true;
  byId('progress').textContent = '';
  for (const id of ['manager-request', 'normalized-constraints', 'solution-summary', 'solutions']) {
    byId(id).replaceChildren();
  }
}

function addStage(values = {}) {
  state.stages.push({ key: String(++nextKey), id: '', name: '', funded: false, resources: '', ...values });
}

function stageField(stage, field, title, type = 'text') {
  const label = element('label', title, type === 'checkbox' ? 'funded-label' : '');
  const input = document.createElement('input');
  input.type = type;
  input.dataset.field = field;
  if (type === 'checkbox') input.checked = stage[field];
  else input.value = stage[field];
  input.addEventListener('input', () => {
    stage[field] = type === 'checkbox' ? input.checked : input.value;
    clearResults();
    if (field === 'id' || field === 'name') renderDependencies();
  });
  label.append(input);
  return label;
}

function renderStages() {
  byId('stages').replaceChildren();
  for (const stage of state.stages) {
    const row = element('div', undefined, 'stage-row');
    row.append(stageField(stage, 'id', 'id'), stageField(stage, 'name', 'name'),
      stageField(stage, 'funded', 'funded', 'checkbox'), stageField(stage, 'resources', 'resources (po przecinku)'));
    const remove = element('button', 'Usuń etap');
    remove.type = 'button';
    remove.addEventListener('click', () => {
      state.stages = state.stages.filter(item => item.key !== stage.key);
      // Zależność pozostaje do poprawienia; usunięty etap przestaje być wyborem.
      for (const field of ['logicalDependencies', 'legalDependencies']) {
        for (const dependency of state[field]) {
          if (dependency.before === stage.key) dependency.before = '';
          if (dependency.after === stage.key) dependency.after = '';
        }
      }
      clearResults();
      renderStages();
      renderDependencies();
    });
    row.append(remove);
    byId('stages').append(row);
  }
}

function dependencySelect(dependency, field) {
  const label = element('label', field);
  const select = document.createElement('select');
  select.dataset.field = field;
  const placeholder = element('option', 'Wybierz etap');
  placeholder.value = '';
  select.append(placeholder);
  for (const stage of state.stages) {
    const option = element('option', `${stage.id || '(brak id)'} — ${stage.name || '(brak nazwy)'}`);
    option.value = stage.key;
    select.append(option);
  }
  select.value = dependency[field];
  select.addEventListener('change', () => {
    dependency[field] = select.value;
    clearResults();
  });
  label.append(select);
  return label;
}

function renderDependencies() {
  for (const [field, container] of [['logicalDependencies', 'logical-dependencies'], ['legalDependencies', 'legal-dependencies']]) {
    byId(container).replaceChildren();
    for (const dependency of state[field]) {
      const row = element('div', undefined, 'dependency-row');
      const remove = element('button', 'Usuń zależność');
      remove.type = 'button';
      remove.addEventListener('click', () => {
        state[field] = state[field].filter(item => item !== dependency);
        clearResults();
        renderDependencies();
      });
      row.append(dependencySelect(dependency, 'before'), element('span', '→'), dependencySelect(dependency, 'after'), remove);
      byId(container).append(row);
    }
  }
}

// Zbieranie danych formularza, bez wyprowadzania ograniczeń domenowych.
function buildManagerInput() {
  const stageId = key => state.stages.find(stage => stage.key === key)?.id ?? '';
  const dependencies = field => state[field].map(item => ({ before: stageId(item.before), after: stageId(item.after) }));
  return {
    stages: state.stages.map(({ id, name, funded, resources }) => ({
      id, name, funded,
      resources: resources.split(',').map(value => value.trim()).filter(Boolean)
    })),
    logicalDependencies: dependencies('logicalDependencies'),
    legalDependencies: dependencies('legalDependencies')
  };
}

function renderOutput(output, request) {
  const constraints = byId('normalized-constraints');
  constraints.replaceChildren();
  if (output.normalizedConstraints.length === 0) constraints.append(element('p', 'Brak ograniczeń.'));
  else {
    const table = document.createElement('table');
    const head = document.createElement('thead');
    const headings = document.createElement('tr');
    for (const title of ['type', 'first', 'second', 'source']) {
      const th = element('th', title);
      th.scope = 'col';
      headings.append(th);
    }
    head.append(headings);
    const body = document.createElement('tbody');
    for (const constraint of output.normalizedConstraints) {
      const row = document.createElement('tr');
      for (const field of ['type', 'first', 'second', 'source']) row.append(element('td', constraint[field] ?? '—'));
      body.append(row);
    }
    table.append(head, body);
    const scroll = element('div', undefined, 'table-scroll');
    scroll.append(table);
    constraints.append(scroll);
  }
  byId('solution-summary').replaceChildren(
    element('p', `status: ${output.status}`),
    element('p', `optimalGroupCount: ${output.optimalGroupCount ?? '—'}`),
    element('p', `solutionCount: ${output.solutionCount}`)
  );
  const solutions = byId('solutions');
  solutions.replaceChildren();
  if (output.status === 'NO_SOLUTION') solutions.append(element('p', 'Brak rozwiązania spełniającego ograniczenia.'));
  const names = new Map(request.stages.map(stage => [stage.id, stage.name]));
  output.solutions.forEach((solution, index) => {
    const article = element('article', undefined, 'solution');
    article.append(element('h3', `Rozwiązanie ${index + 1}`));
    if (solution.groups.length === 0) article.append(element('p', 'Pusty projekt — brak grup.'));
    for (const group of solution.groups) {
      const block = element('div', undefined, 'group');
      const list = document.createElement('ul');
      for (const id of group.stages) list.append(element('li', `${id} - ${names.get(id) ?? id}`));
      block.append(element('h4', `Grupa ${group.groupNumber}`), list);
      article.append(block);
    }
    solutions.append(article);
  });
}

byId('add-stage').addEventListener('click', () => {
  addStage(); clearResults(); renderStages(); renderDependencies();
});
for (const [button, field] of [['add-logical', 'logicalDependencies'], ['add-legal', 'legalDependencies']]) {
  byId(button).addEventListener('click', () => {
    state[field].push({ before: '', after: '' }); clearResults(); renderDependencies();
  });
}
byId('reset').addEventListener('click', () => {
  state.stages = []; state.logicalDependencies = []; state.legalDependencies = [];
  clearResults(); renderStages(); renderDependencies();
});
byId('load-example').addEventListener('click', () => {
  state.stages = []; state.logicalDependencies = []; state.legalDependencies = [];
  for (const [id, name, funded, resources] of [
    ['A', 'Analiza', true, 'Koparka'], ['B', 'Zakupy', true, 'Koparka'],
    ['C', 'Badania', true, 'Laboratorium'], ['D', 'Eksperyment', false, 'Laboratorium']
  ]) addStage({ id, name, funded, resources });
  state.logicalDependencies.push({ before: state.stages[0].key, after: state.stages[2].key });
  state.legalDependencies.push({ before: state.stages[2].key, after: state.stages[3].key });
  clearResults(); renderStages(); renderDependencies();
});
byId('manager-form').addEventListener('submit', async event => {
  event.preventDefault();
  clearResults();
  const request = buildManagerInput();
  byId('manager-request').textContent = JSON.stringify(request, null, 2);
  byId('results').hidden = false;
  byId('editor').disabled = true;
  byId('progress').textContent = 'Trwa obliczanie…';
  try {
    let response;
    try {
      response = await fetch('/api/parallelization', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request)
      });
    } catch {
      throw new Error('API nie jest dostępne. Sprawdź, czy lokalny serwer działa.');
    }
    const output = await response.json();
    if (!response.ok) throw new Error(output.error || `Błąd API (HTTP ${response.status}).`);
    renderOutput(output, request);
    byId('progress').textContent = 'Obliczenia zakończone.';
  } catch (error) {
    byId('error').textContent = error.message;
    byId('error').hidden = false;
    byId('progress').textContent = '';
  } finally {
    byId('editor').disabled = false;
  }
});

renderStages();
renderDependencies();
