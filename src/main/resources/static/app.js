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
  for (const id of ['manager-request', 'normalized-constraints', 'solution-summary', 'solutions', 'all-solutions']) {
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
    element('p', `Znaleziono optymalnych rozwiązań: ${output.totalSolutionCount}`),
    element('p', `Zwrócono: ${output.returnedSolutionCount}`),
    element('p', `Selection policy: ${output.selectionPolicy.rules.join(' \u2192 ')}`)
  );
  const solutions = byId('solutions');
  solutions.replaceChildren();
  if (output.status === 'NO_SOLUTION') solutions.append(element('p', 'Brak rozwiązania spełniającego ograniczenia.'));
  const names = new Map(request.managerInput.stages.map(stage => [stage.id, stage.name]));
  function renderSolution(solution, index, target) {
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
    target.append(article);
  }
  if (output.selectedSolution) renderSolution(output.selectedSolution, 0, solutions);
  byId('all-solutions-section').hidden = !request.returnAllSolutions;
  byId('all-solutions').replaceChildren();
  if (request.returnAllSolutions) output.allSolutions.forEach((solution, index) => renderSolution(solution, index, byId('all-solutions')));
}

byId('add-stage').addEventListener('click', () => {
  addStage(); clearResults(); renderStages(); renderDependencies();
});
for (const [button, field] of [['add-logical', 'logicalDependencies'], ['add-legal', 'legalDependencies']]) {
  byId(button).addEventListener('click', () => {
    state[field].push({ before: '', after: '' }); clearResults(); renderDependencies();
  });
}
byId('return-all-solutions').addEventListener('change', clearResults);
byId('reset').addEventListener('click', () => {
  byId('return-all-solutions').checked = false;
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
  const request = { returnAllSolutions: byId('return-all-solutions').checked, managerInput: buildManagerInput() };
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
