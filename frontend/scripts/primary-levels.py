#!/usr/bin/env python3
"""Écrit src/features/wiki/PagesAssistant/PagesAssistantAISteps/primaryLevels.ts depuis le
référentiel CASE des programmes de l'école primaire (module case, programmes-1d.json).

Pour chaque niveau CP→CM2 : une matière par enseignement du programme, et pour séquences ses
sous-domaines (ou ses domaines quand il n'y en a pas). Les rubriques d'un programme abrogé ne sont
gardées que si l'enseignement n'en a pas d'autre.

    python3 scripts/primary-levels.py ../../case/src/main/resources/referentiels/programmes-1d.json
"""
import json
import re
import sys
from pathlib import Path

LEVELS = ['CP', 'CE1', 'CE2', 'CM1', 'CM2']
ICONS = {
    'Français': 'francaisIcon',
    'Mathématiques': 'mathematiquesIcon',
    'Histoire-géographie': 'histoireIcon',
    'Enseignement moral et civique': 'educationMoraleCiviqueIcon',
    'Éducation physique et sportive': 'epsIcon',
    'Arts plastiques': 'artsPlastiquesIcon',
    'Éducation musicale': 'educationMusicaleIcon',
    'Histoire des arts': 'artsPlastiquesIcon',
    'Sciences et technologie': 'enseignementScientifiqueIcon',
    'Questionner le monde': 'enseignementScientifiqueIcon',
    'Langues vivantes étrangères et régionales': 'anglaisIcon',
}
OUT = Path(__file__).resolve().parent.parent / 'src/features/wiki/PagesAssistant/PagesAssistantAISteps/primaryLevels.ts'

pkg = json.loads(Path(sys.argv[1]).read_text())
items = {i['identifier']: i for i in pkg['CFItems']}
children = {}
for a in pkg['CFAssociations']:
    if a['associationType'] == 'isChildOf':
        children.setdefault(a['destinationNodeURI']['identifier'], []).append(a['originNodeURI']['identifier'])


def order(identifier):
    return [int(n) if n.isdigit() else n for n in re.split(r'(\d+)', items[identifier].get('humanCodingScheme', ''))]


def under(identifier, level, kind):
    return [k for k in sorted(children.get(identifier, []), key=order)
            if items[k].get('CFItemType') == kind and level in items[k].get('educationLevel', [level])]


def clean(statement):
    statement = re.sub(r'^(Cours (préparatoire|élémentaire|moyen)[^:]*|CP|CE1|CE2|CM1|CM2)\s*:\s*', '', statement)
    return re.sub(r'^Thème \d+\s*:\s*', '', statement).strip().rstrip('.')


def subjects(level):
    result = []
    for item in sorted(items.values(), key=lambda i: i.get('humanCodingScheme', '')):
        if item.get('CFItemType') != 'Enseignement' or level not in item.get('educationLevel', []):
            continue
        domains = under(item['identifier'], level, 'Domaine')
        domains = [d for d in domains if not items[d].get('statusEndDate')] or domains
        sequences = []
        for domain in domains:
            parts = under(domain, level, 'Sous-domaine') or [domain]
            sequences += [clean(items[p]['fullStatement']) for p in parts]
        sequences = [s for s in dict.fromkeys(sequences) if s and not s.startswith('Programme de')]
        if sequences:
            result.append((item['fullStatement'], sequences))
    return result


lines = [
    '// Généré par scripts/primary-levels.py depuis le référentiel CASE des programmes de l’école',
    '// primaire (module case) : ne pas modifier à la main.',
    "import anglaisIcon from '../icons/subjects/anglais.png';",
    "import artsPlastiquesIcon from '../icons/subjects/arts_plastiques.png';",
    "import educationMoraleCiviqueIcon from '../icons/subjects/education_morale_civique.png';",
    "import educationMusicaleIcon from '../icons/subjects/education_musicale.png';",
    "import enseignementScientifiqueIcon from '../icons/subjects/enseignement_scientifique.png';",
    "import epsIcon from '../icons/subjects/eps.png';",
    "import francaisIcon from '../icons/subjects/francais.png';",
    "import genericSubjectIcon from '../icons/subjects/genericSubject.png';",
    "import histoireIcon from '../icons/subjects/histoire.png';",
    "import mathematiquesIcon from '../icons/subjects/mathematiques.png';",
    '',
    'export const primaryLevels = [',
]
for level in LEVELS:
    lines += ['  {', f'    value: {json.dumps(level)},', '    subjects: [']
    for subject, sequences in subjects(level):
        lines += ['      {', f'        value: {json.dumps(subject, ensure_ascii=False)},',
                  f"        image: {ICONS.get(subject, 'genericSubjectIcon')},", '        sequences: [']
        lines += [f'          {json.dumps(s, ensure_ascii=False)},' for s in sequences]
        lines += ['        ],', '      },']
    lines += ['    ],', '  },']
lines += ['];', '']
OUT.write_text('\n'.join(lines))
print(f'{OUT.name} : {sum(len(subjects(l)) for l in LEVELS)} matières sur {len(LEVELS)} niveaux')
