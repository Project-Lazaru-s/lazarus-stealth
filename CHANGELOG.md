# Changelog

## 0.2.0 — 2026-10-17

- **Perfil humano** (tag `lazarusstealth:human`, seção `[human]` do config). Os NPCs humanos hostis veem até 40 blocos, com cone de 140° e periferia de 240°, detectam 1,5× mais rápido, enxergam melhor no escuro e lembram da última posição por 20 s. Também se aproximam devagar de ruídos baixos e flanqueiam tiros.
- Comunicação de grupo: quem vê ou ouve o jogador alerta os aliados num raio de 16 blocos.
- Alvo recusado por outro mod (aviso, pedágio, posto): o NPC continua "sabendo" do jogador enquanto o vê, sem caçá-lo.
- O bloqueio de alvo passou para prioridade HIGH, então outros mods só veem tentativas de alvo de quem já percebeu o jogador.
- Nova API pública `StealthApi`: hostilidade, aliados, consciência, revelação e eventos de mudança de estado.
- Testes: 7 novos GameTests com o Lazarus NPCs. Cada teste agora limpa os mobs e jogadores deixados pelos anteriores.

## 0.1.2 — 2026-10-17

- Tiro com silenciador passa a ser ruído **Baixo** (antes Alto): zumbis até 16 blocos só investigam o ponto do tiro.

## 0.1.1 — 2026-10-17

- Novo nível de ruído **Extremo** (alcance de 40 blocos ou mais), reservado aos tiros do TaCZ sem silenciador. Tiro silenciado agora é **Baixo**: zumbis até 16 blocos investigam o ponto do tiro, sem entrar em Alerta. Nenhum passo alcança Extremo, e o alcance e a reação dos zumbis não mudaram: o nível só separa, no indicador e na prioridade das pistas, o tiro da corrida em metal ou vidro.
- Indicador redesenhado: ícone de alto-falante com 0 a 4 ondas, semitransparente, no canto inferior esquerdo (`offsetLeft` 14 e `offsetBottom` 50 no config do cliente). Sem texto.

## 0.1.0 — 2026-10-17

Primeira versão.

- Percepção por par infectado–jogador: Desatento, Desconfiado, Investigando, Alerta e Perseguindo. Só o estado Perseguindo deixa um infectado ter o jogador como alvo, seja qual for o mod que tente dar o alvo (vanilla, Zombie Awareness, Dual Hitbox, Apocalypse Now, Undead Revamped).
- Visão com cone frontal e periferia, raios de oclusão por parte do corpo (blocos `see_through` não escondem), luz, postura real (agachado e rastejando, inclusive o rastejar forçado do Smart Moving), velocidade e habilidade. A suspeita acumula com o tempo.
- Memória: última posição conhecida (6 s), busca local (8 s) e calma gradual. Agachar não encerra uma perseguição.
- Audição: ruídos levam o infectado ao ponto do som, nunca ao jogador. Paredes abafam e há limite de reações por ruído.
- Passos por distância, superfície (tags `surface_soft`, `surface_metal`, `surface_glass`), postura, carga (Encumbered) e exaustão (Lazarus Moodles). Também fazem ruído: quedas, respiração, portas, contêineres, coleta, golpes, quebra de blocos e de vidro e tiros do TaCZ.
- Finalização furtiva no ataque corpo a corpo normal, validada no servidor. Mata infectados comuns e dá crítico controlado nos especiais. Gasta fôlego do PeakStamina, tem recarga e preserva crédito, drops e estatísticas. Nos zumbis do Dual Hitbox vira golpe na cabeça.
- Árvore **Furtividade** no Puffish Skills (4 níveis, 4 habilidades por atributos) com XP por evasão, investigação despistada e finalização, e proteção contra farm.
- Indicador de ruído Nenhum/Baixo/Médio/Alto. O protocolo já leva o estado de ameaça para um indicador futuro.
- Comandos `/lazarusstealth debug` e `/lazarusstealth status`.
- Requer Lazarus Moodles 1.15.1 ou mais novo (nova API `LazarusMoodlesApi.exhaustionLevel`), se o Moodles estiver instalado.
