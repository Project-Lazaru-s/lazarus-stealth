# Lazarus Stealth

**Lazarus Stealth** é um mod para Minecraft Forge 1.20.1 (Forge 47.4.10) feito para o modpack **Project Lazarus**. Ele faz postura, cobertura, luz, ruído, peso, exaustão e habilidade mudarem de verdade a percepção dos infectados: é possível passar agachado a poucos blocos de um zumbi desatento, despistar uma investigação e finalizá-lo em silêncio, sem que isso vire invisibilidade.

Toda a detecção, o XP e o dano são decididos no servidor. O cliente só recebe o próprio nível de ruído para o indicador.

---

## Como os infectados percebem você

Cada par **infectado–jogador** tem um estado próprio:

| Estado | O que o zumbi faz |
|---|---|
| **Desatento** | Vaga e olha ao redor; não sabe de você. |
| **Desconfiado** | Viu algo de relance: para e olha para onde viu. |
| **Investigando** | Vai até o ponto onde ouviu um som ou viu um vulto, sem saber onde você está agora. |
| **Alerta** | Ouviu algo alto, ouviu vários ruídos seguidos ou perdeu você de vista; procura e enxerga 2,5× mais rápido. |
| **Perseguindo** | Tem você como alvo. Só este estado permite que ele ataque. |

**Visão.** A cada 6 ticks o servidor mede distância (até 24 blocos), o ângulo do olhar do zumbi (cone frontal de 110°, periferia lenta até 200°, nada atrás), quantos pontos do seu corpo ele enxerga (cabeça, tronco e quadril; três pontos baixos ao rastejar), a luz onde você está, sua postura, sua velocidade e a habilidade **Corpo Baixo**. Isso enche uma barra de suspeita: um cruzamento rápido na periferia passa, ficar parado na frente dele acaba em descoberta. Cercas, grades e vidro não escondem ninguém. Paredes, móveis, carros e folhagem escondem.

- Encostar no zumbi (≈1,2 bloco) é descoberta imediata.
- Tocha, lanterna ou glowstone na mão, estar em chamas ou brilhando contam como luz forte.
- Ser atingido pelo jogador revela o jogador.
- **Agachar não apaga uma perseguição.** Quando perde você de vista, o zumbi vai até a última posição conhecida (6 s), procura em volta (8 s) e só então se acalma.

**Audição.** Todo ruído tem posição e alcance. Um zumbi que ouve vai **até o ponto do som**, nunca direto até você:

- **Baixo:** ele investiga o ponto do som.
- **Médio:** ele investiga mais rápido. Três ruídos médios em 10 s deixam o zumbi em Alerta.
- **Alto:** ele fica em Alerta e corre até o ponto.

Paredes entre o som e o zumbi reduzem o alcance a 60%. No máximo 10 infectados reagem a cada ruído.

**Zumbis sem cabeça** (Dual Hitbox) não usam a visão do mod: continuam guiados só por ruído, pela regra do próprio Dual Hitbox.

## Ruído

O ruído é gerado no servidor, a cada 1,6 bloco andado no chão, a partir do bloco sob os pés:

| Superfície (tag) | Andando | Correndo | Agachado | Rastejando |
|---|---|---|---|---|
| Macia (`surface_soft`: grama, terra, folhas, lã, areia, neve) | 5 (Baixo) | 8,5 (Médio) | 2 (Baixo) | 1,5 (Baixo) |
| Comum (sem tag: madeira, pedra, concreto, asfalto) | 8 (Médio) | 13,6 (Médio) | 3,2 (Baixo) | 2,4 (Baixo) |
| Metal (`surface_metal`: ferro, cobre, grades, chapas, dutos do Apocalypse Now, ferro do Immersive Weathering) | 12 (Médio) | 20,4 (Alto) | 4,8 (Baixo) | 3,6 (Baixo) |
| Vidro (`surface_glass`: vidro, painéis, `glass_spikes`) | 14 (Alto) | 23,8 (Alto) | 14 (Alto) | 14 (Alto) |

Valores em blocos de alcance. Classes: abaixo de 1,5 = **Nenhum** (nada é emitido), até 6 = **Baixo**, até 14 = **Médio**, até 40 = **Alto**, 40 ou mais = **Extremo**. Nenhum passo chega a Extremo (o teto dos passos é 2,5× a superfície mais alta, 35 blocos): esse nível é dos tiros.

Outras ações:

- **Sem desconto de postura nem de habilidade:**
  - quebrar um bloco: 14 (o mesmo alcance do Dual Hitbox);
  - quebrar vidro: 18;
  - tiro do TaCZ: 64 (**Extremo**), ou 16 com silenciador (**Alto**); o chamado dos zumbis continua com o script `lazarus_gunshot_noise.js`.
- **Pela metade se você estiver agachado:**
  - portas, alçapões e portões: 6;
  - abrir contêiner: 4;
  - pegar itens: 2.
- Golpe corpo a corpo: 8 (6 numa finalização).
- Queda acima de 2 blocos: alcance proporcional à altura.
- Respiração pesada com Exaustão 4–5, parado.

**Peso e exaustão.** Os dois aumentam os passos. Os valores vêm do **Encumbered** (fração da capacidade efetiva) e da API do **Lazarus Moodles** (nível efetivo de Exaustão), lidos uma vez por segundo:

| Carga | Passos |
|---|---|
| até 50% | sem acréscimo |
| 80% | +15% |
| 100% ou mais | +30% |

| Exaustão | 0–2 | 3 | 4 | 5 |
|---|---|---|---|---|
| Passos | — | +10% | +25% | +40% |

O multiplicador total fica entre 0,15× e 2,5×.

## Finalização furtiva

Basta um **ataque corpo a corpo normal** com arma branca (espadas, machados, `#lazarusstealth:finisher_weapons`) em um infectado que **não percebeu você**. O servidor confere:

- alvo infectado, fora de `finisher_immune`;
- zumbi sem alvo, sem investigar nada e sem suspeita de você;
- você atrás ou de lado (ângulo de 100°, ou 80° com **Finalizador**);
- até 3,5 blocos, com linha de visão;
- ataque carregado (90%);
- 15 de fôlego do PeakStamina;
- 6 s de recarga.

O resultado depende do alvo:

- **Comuns:** morte no mesmo golpe.
- **Especiais** (tag `finisher_special`: Heavy, Bomber, Spitter, Smoker, Hunter e Sucker do Undead Revamped, ou mais de 40 de vida): dano crítico de 35% da vida máxima e lentidão curta, sem morte automática.

O golpe é o ataque vanilla com o valor ajustado, por isso a fonte de dano, o crédito pela morte, os drops, as estatísticas e o XP de Combate continuam os de sempre, e nada é aplicado duas vezes. Nos zumbis do **Dual Hitbox** (que cancelam todo dano comum e usam suas próprias reservas de vida), a finalização vira um golpe na cabeça pela API pública do Dual Hitbox. É uma morte por cérebro, creditada ao jogador.

Fôlego e recarga só são gastos se o golpe acertar. Golpe num zumbi atento é um golpe comum e o faz perseguir você.

## Árvore Furtividade (Puffish Skills)

Categoria `furtividade`, com 4 níveis (40, 70, 100 e 130 XP) e 4 habilidades em sequência. As recompensas são atributos (`lazarusstealth:*`), removidos automaticamente no respec.

| Habilidade | Efeito |
|---|---|
| **Passos Leves** | Passos alcançam 30% menos. Não abafa tiros, mineração nem vidro quebrado. |
| **Corpo Baixo** | Agachado ou rastejando, a suspeita sobe 40% mais devagar (12% em pé). |
| **Leitura de Rotas** | Metade da penalidade de peso e exaustão; o ruído extra de metal e vidro cai 25%; passos alcançam mais 10% menos. |
| **Finalizador** | Finalização com 40% menos fôlego e recarga, ângulo de 80° e crítico 50% maior nos especiais. |

**XP só por risco real:**

| Situação | XP |
|---|---|
| Passar agachado ou rastejando (pelo menos 4 blocos) a até 4 blocos de um infectado e sair da área (10 blocos) sem ser perseguido | 2 (4 se passar a até 3 blocos) |
| Um infectado investigar um ruído seu e desistir sem achar você | 2 |
| Finalização | 3 |

**Proteção contra farm:**

- cada zumbi paga uma vez a cada 10 minutos;
- no máximo 3 recompensas por área de 16×16 a cada 5 minutos;
- teto de 60 XP por dia de jogo;
- nada em criativo ou espectador;
- nada de zumbis de spawner, ovo, dispensador ou `/summon` (este último é configurável), sem IA, presos por laço ou montados.

O XP entra pelo mesmo comando usado pelas outras árvores do Lazarus (`puffish_skills experience add`), e uma falha do comando é registrada no log. O XP não é perdido ao morrer.

## Indicador de ruído

Um alto-falante no canto inferior esquerdo, logo acima da linha da hotbar, com 0 a 4 ondas: **Nenhum**, **Baixo**, **Médio**, **Alto** e **Extremo** (só tiros sem silenciador). Dá para ler pela quantidade de ondas, sem depender da cor. O ícone é semitransparente e some 3 s depois do último ruído, a não ser que `alwaysShowNoiseIndicator` esteja ligado. A posição pode ser ajustada com `offsetLeft` e `offsetBottom` no `lazarusstealth-client.toml`.

O indicador mostra o último ruído que **você** fez e seu decaimento (um nível a cada segundo, depois de 1,5 s). Ele não mede luz, cheiro de sangue nem o quanto você está visível.

O protocolo já envia um estado agregado (**Investigando / Perseguindo**) para um indicador de ameaça separado, previsto para uma versão futura e ainda não desenhado.

## Comandos (operadores)

- `/lazarusstealth debug [true|false]`: mostra na barra de ação sua postura, o ruído, a carga e a exaustão, além do estado, da suspeita, do ritmo e do motivo da última transição do infectado mais próximo.
- `/lazarusstealth status`: integrações encontradas, custo médio da percepção e quantos alvos do Zombie Awareness foram recusados.

## Configuração

- `serverconfig/lazarusstealth-server.toml` (por mundo; mundos novos copiam de `defaultconfigs/`): cones, alcance, ritmo de detecção, memória, alcances de cada superfície e ação, finalização e XP.
- `config/lazarusstealth-client.toml`: indicador ligado ou desligado, sempre visível e posição.
- **Tags de dados**, para incluir blocos e mobs de outros mods sem mexer no código:
  - entidades: `lazarusstealth:infected`, `finisher_special`, `finisher_immune`, `no_vision`;
  - blocos: `surface_soft`, `surface_metal`, `surface_glass`, `see_through`;
  - itens: `finisher_weapons`, `light_sources`.

## Integrações (verificadas contra os JARs do perfil)

| Mod | Como integra |
|---|---|
| **Zombie Awareness 1.13.1** | Seu alvo por visão (16 blocos, linha de visão sem cone) passa por `Mob.setTarget` → `LivingChangeTargetEvent`, que o mod recusa enquanto o par não está Perseguindo. Também é desfeito o caminho até o jogador que o ZA inicia logo antes de dar o alvo. Som, cheiro, luz e hordas errantes do ZA continuam. Nenhuma configuração do ZA foi alterada. |
| **Dual Hitbox Zombies 1.0.0** | A atração por barulho (`setTarget(player)` ao quebrar blocos ou lutar) é recusada e substituída por uma pista até o ponto do som. Zumbis sem cabeça ficam com a regra do próprio DHZ. A finalização usa `ZombieCombatEvents.applyHeadDamage`. Isca e jarra de cérebro (que só limpam o alvo) não são afetadas. |
| **Smart Moving Reborn 1.0.0** | A postura vem do estado real no servidor: o SMR põe o jogador em `Pose.SWIMMING` ao rastejar, inclusive no rastejar forçado em vãos de 1 bloco. Agachar vem de `Pose.CROUCHING`. A tecla não é lida. |
| **TaCZ 1.1.8** | `GunFireEvent`: tiro **Extremo** no indicador (**Alto** com silenciador) e infectados no alcance (64 ou 16) entram em Alerta com pista no ponto do tiro. O script `lazarus_gunshot_noise.js` continua dono do chamado, e caminhos que já terminam perto do tiro são mantidos. |
| **Puffish Skills 0.19.2** | Categoria `furtividade`, atributos como recompensa e XP por comando. |
| **PeakStamina 2.2.2** | Lê e gasta `StaminaCapability.stamina`. |
| **Encumbered 0.99** | `WeightEvent.calculateWeight` ÷ `getWeightWithBoostItem(player, 1)` (capacidade efetiva com itens de bônus e encantamento). |
| **Lazarus Moodles 1.15.1** | Nova API pública `LazarusMoodlesApi.exhaustionLevel`, com o nível efetivo, inclusive a abstinência de sedativos. |
| **Apocalypse Now, Undead Revamped, The Hordes, vanilla** | Cobertos pela tag `infected`. As hordas do The Hordes continuam rastreando o jogador por conta própria (é um evento) e precisam vê-lo ou tocá-lo para atacar. |

Sem algum desses mods, o Lazarus Stealth usa valores neutros e registra um aviso uma única vez. Nenhum JAR de terceiros foi alterado. A IA dos infectados ganha só dois objetivos novos, e somente nos mobs com a tag `infected`: investigar uma pista e olhar ao redor sem virar a cabeça para jogadores que ainda não percebeu (o `LookAtPlayerGoal` vanilla viraria a cabeça para quem está atrás).

## Instalação no Project Lazarus

1. Coloque `lazarusstealth-0.1.0.jar` e `lazarusmoodles-1.15.1.jar` (no lugar do 1.15.0) em `mods/`, no cliente **e** no servidor.
2. Copie `extras/puffish_skills/categories/furtividade/` para `config/puffish_skills/categories/` e acrescente `"furtividade"` à lista `categories` de `config/puffish_skills/config.json`.
3. Copie `extras/defaultconfigs/lazarusstealth-server.toml` para `defaultconfigs/`. Mundos novos e mundos existentes que ainda não têm o arquivo recebem a mesma regra na próxima vez que carregarem.

**Para desfazer:** remova o JAR, a pasta `furtividade` e a entrada em `config.json`. O mod não grava nada no mundo além de um marcador `lazarusstealth_farm` em zumbis de spawner. Percepção, ruído e recargas ficam só em memória, e o XP já ganho fica guardado pelo Puffish.

## Desenvolvimento

```
./gradlew build --offline               # compila e roda StealthMathTest (equilíbrio, com asserts)
./gradlew runGameTestServer --offline   # 21 GameTests no mundo, com os JARs do pack em libs/
```

- `libs/` recebe cópias locais dos JARs do perfil (TaCZ para compilar; os demais só no runtime dos testes) e não é versionada.
- `run/config/puffish_skills` precisa das categorias `coragem` e `furtividade` para o teste do Puffish.

## Limitações conhecidas (0.1.0)

- Indicador separado de ameaça, objetos de distração e animação própria de finalização ficam para depois.
- Lanternas acopladas às armas do TaCZ não contam como luz na mão. Luz dinâmica (Sodium Dynamic Lights) é só visual e não muda a luz do servidor.
- O ruído atravessa no máximo uma checagem de oclusão (um raio). Paredes finas e grossas abafam igual.
- Os alcances são metas iniciais, a calibrar em jogo.

## Créditos e licença

Kaique Melo (Project Lazarus). Todos os direitos reservados.
