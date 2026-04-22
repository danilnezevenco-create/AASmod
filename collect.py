import os

# --- НАСТРОЙКИ ---
# Имя файла, который мы создадим
output_filename = 'full_project_code.txt'

# Какие файлы мы хотим видеть (расширения)
# .java - сам код
# .json - модели, рецепты, лут-таблицы
# .toml - настройки мода
# .gradle - зависимости и версии
included_extensions = ['.java', '.json', '.toml', '.gradle']

# Какие папки мы ИГНОРИРУЕМ (чтобы не мусорить)
ignored_directories = {
    '.gradle', 'build', 'run', '.git', '.idea',
    'gradle', '.settings', 'bin'
}

def collect_code():
    cwd = os.getcwd() # Текущая папка

    with open(output_filename, 'w', encoding='utf-8') as outfile:
        # Пишем заголовок
        outfile.write(f"СБОРКА ПРОЕКТА: {os.path.basename(cwd)}\n")
        outfile.write("="*50 + "\n\n")

        # Проходимся по всем папкам
        for root, dirs, files in os.walk(cwd):
            # Удаляем игнорируемые папки из списка обхода
            dirs[:] = [d for d in dirs if d not in ignored_directories]

            for file in files:
                # Проверяем расширение файла
                if any(file.endswith(ext) for ext in included_extensions):
                    file_path = os.path.join(root, file)
                    # Получаем относительный путь (например src/main/java/...)
                    rel_path = os.path.relpath(file_path, cwd)

                    try:
                        with open(file_path, 'r', encoding='utf-8') as infile:
                            content = infile.read()

                            # Записываем красивый разделитель и путь к файлу
                            outfile.write(f"\n{'='*20} ФАЙЛ: {rel_path} {'='*20}\n")
                            outfile.write(content + "\n")
                            print(f"Добавлен: {rel_path}")
                    except Exception as e:
                        print(f"Ошибка чтения {rel_path}: {e}")

    print(f"\nГОТОВО! Все сохранено в файл: {output_filename}")

if __name__ == '__main__':
    collect_code()
