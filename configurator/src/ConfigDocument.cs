using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Text;

namespace MouseMasterConfigurator
{
    internal sealed class ConfigDocument
    {
        public const string DisabledPrefix = "# mmcfg-disabled: ";
        public const string MetadataBegin = "# --- MouseMaster Configurator state: begin ---";
        public const string MetadataEnd = "# --- MouseMaster Configurator state: end ---";

        private readonly List<string> lines;
        private readonly string newLine;

        private ConfigDocument(IEnumerable<string> sourceLines, string newLine)
        {
            lines = new List<string>(sourceLines);
            this.newLine = newLine;
        }

        public static ConfigDocument Parse(string text)
        {
            if (text == null)
                throw new ArgumentNullException("text");

            string newline = text.Contains("\r\n") ? "\r\n" : "\n";
            string normalized = text.Replace("\r\n", "\n").Replace('\r', '\n');
            var split = new List<string>(normalized.Split('\n'));
            if (split.Count > 0 && split[split.Count - 1].Length == 0)
                split.RemoveAt(split.Count - 1);
            return new ConfigDocument(split, newline);
        }

        public string GetText()
        {
            return string.Join(newLine, lines.ToArray()) + newLine;
        }

        public bool HasActiveProperty(string propertyKey)
        {
            return FindSpan(propertyKey) != null;
        }

        public string GetPropertyValue(string propertyKey)
        {
            PropertySpan span = FindSpan(propertyKey);
            if (span == null)
                return null;

            var builder = new StringBuilder();
            for (int index = span.Start; index <= span.End; index++)
            {
                string part = lines[index].Trim();
                if (index == span.Start)
                {
                    int equalsIndex = part.IndexOf('=');
                    part = equalsIndex >= 0 ? part.Substring(equalsIndex + 1).Trim() : string.Empty;
                }
                if (part.EndsWith("\\", StringComparison.Ordinal))
                    part = part.Substring(0, part.Length - 1).TrimEnd();
                if (builder.Length > 0)
                    builder.Append(' ');
                builder.Append(part);
            }
            return builder.ToString().Trim();
        }

        public string FindAliasPropertyKey(string aliasName)
        {
            string baseKey = "key-alias." + aliasName;
            foreach (PropertySpan span in EnumerateSpans())
            {
                if (string.Equals(span.Key, baseKey, StringComparison.OrdinalIgnoreCase) ||
                    span.Key.StartsWith(baseKey + ".", StringComparison.OrdinalIgnoreCase))
                    return span.Key;
            }
            return null;
        }

        public void SetProperty(string propertyKey, string propertyValue)
        {
            if (string.IsNullOrWhiteSpace(propertyKey))
                throw new ArgumentException("Property key cannot be blank.", "propertyKey");
            if (string.IsNullOrWhiteSpace(propertyValue))
                throw new ArgumentException("Property value cannot be blank.", "propertyValue");

            PropertySpan span = FindSpan(propertyKey);
            string replacement = propertyKey + "=" + propertyValue.Trim();
            if (span != null)
            {
                lines.RemoveRange(span.Start, span.End - span.Start + 1);
                lines.Insert(span.Start, replacement);
                return;
            }

            int insertionIndex = MetadataStartIndex();
            if (insertionIndex < 0)
                insertionIndex = lines.Count;
            if (insertionIndex > 0 && lines[insertionIndex - 1].Length != 0)
                lines.Insert(insertionIndex++, string.Empty);
            lines.Insert(insertionIndex, replacement);
        }

        public void RemoveProperty(string propertyKey)
        {
            PropertySpan span = FindSpan(propertyKey);
            if (span != null)
                lines.RemoveRange(span.Start, span.End - span.Start + 1);
        }

        public void CommentProperty(string propertyKey)
        {
            PropertySpan span = FindSpan(propertyKey);
            if (span == null)
                return;
            for (int index = span.Start; index <= span.End; index++)
                lines[index] = DisabledPrefix + lines[index];
        }

        public void RestoreManagedComments()
        {
            for (int index = 0; index < lines.Count; index++)
            {
                if (lines[index].StartsWith(DisabledPrefix, StringComparison.Ordinal))
                    lines[index] = lines[index].Substring(DisabledPrefix.Length);
            }
        }

        public IDictionary<string, string> ReadMetadata()
        {
            var result = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
            int start = MetadataStartIndex();
            if (start < 0)
                return result;
            int end = MetadataEndIndex(start);
            if (end < 0)
                return result;

            for (int index = start + 1; index < end; index++)
            {
                string line = lines[index].Trim();
                if (!line.StartsWith("# mmcfg.", StringComparison.OrdinalIgnoreCase))
                    continue;
                string content = line.Substring(2);
                int equalsIndex = content.IndexOf('=');
                if (equalsIndex <= 0)
                    continue;
                result[content.Substring(0, equalsIndex).Trim()] =
                    content.Substring(equalsIndex + 1).Trim();
            }
            return result;
        }

        public void RemoveMetadata()
        {
            int start = MetadataStartIndex();
            if (start < 0)
                return;
            int end = MetadataEndIndex(start);
            if (end < 0)
                end = lines.Count - 1;
            lines.RemoveRange(start, end - start + 1);
            while (lines.Count > 0 && lines[lines.Count - 1].Length == 0)
                lines.RemoveAt(lines.Count - 1);
        }

        public void AppendMetadata(IEnumerable<string> metadataLines)
        {
            RemoveMetadata();
            if (lines.Count > 0 && lines[lines.Count - 1].Length != 0)
                lines.Add(string.Empty);
            lines.Add(MetadataBegin);
            foreach (string line in metadataLines)
                lines.Add("# " + line);
            lines.Add(MetadataEnd);
        }

        public IList<string> DuplicateActivePropertyKeys()
        {
            return EnumerateSpans()
                .GroupBy(delegate(PropertySpan span) { return span.Key; }, StringComparer.OrdinalIgnoreCase)
                .Where(delegate(IGrouping<string, PropertySpan> group) { return group.Count() > 1; })
                .Select(delegate(IGrouping<string, PropertySpan> group) { return group.Key; })
                .ToList();
        }

        public IList<string> InvalidActivePropertyLines()
        {
            var invalid = new List<string>();
            foreach (PropertySpan span in EnumerateSpans())
            {
                string value = GetPropertyValue(span.Key);
                if (string.IsNullOrWhiteSpace(span.Key) || string.IsNullOrWhiteSpace(value))
                    invalid.Add(lines[span.Start]);
            }
            return invalid;
        }

        public IList<KeyValuePair<string, string>> ActivePropertiesStartingWith(string prefix)
        {
            var result = new List<KeyValuePair<string, string>>();
            foreach (PropertySpan span in EnumerateSpans())
            {
                if (!span.Key.StartsWith(prefix, StringComparison.OrdinalIgnoreCase))
                    continue;
                result.Add(new KeyValuePair<string, string>(
                    span.Key,
                    GetPropertyValue(span.Key)));
            }
            return result;
        }

        private IEnumerable<PropertySpan> EnumerateSpans()
        {
            int index = 0;
            while (index < lines.Count)
            {
                string trimmed = lines[index].TrimStart();
                if (trimmed.Length == 0 || trimmed.StartsWith("#", StringComparison.Ordinal))
                {
                    index++;
                    continue;
                }

                int equalsIndex = trimmed.IndexOf('=');
                if (equalsIndex <= 0)
                {
                    index++;
                    continue;
                }

                string key = trimmed.Substring(0, equalsIndex).Trim();
                int end = index;
                while (end < lines.Count - 1 && lines[end].TrimEnd().EndsWith("\\", StringComparison.Ordinal))
                    end++;
                yield return new PropertySpan(key, index, end);
                index = end + 1;
            }
        }

        private PropertySpan FindSpan(string propertyKey)
        {
            return EnumerateSpans().FirstOrDefault(
                delegate(PropertySpan span)
                {
                    return string.Equals(span.Key, propertyKey, StringComparison.OrdinalIgnoreCase);
                });
        }

        private int MetadataStartIndex()
        {
            for (int index = 0; index < lines.Count; index++)
            {
                if (string.Equals(lines[index].Trim(), MetadataBegin, StringComparison.Ordinal))
                    return index;
            }
            return -1;
        }

        private int MetadataEndIndex(int start)
        {
            for (int index = start + 1; index < lines.Count; index++)
            {
                if (string.Equals(lines[index].Trim(), MetadataEnd, StringComparison.Ordinal))
                    return index;
            }
            return -1;
        }

        private sealed class PropertySpan
        {
            public PropertySpan(string key, int start, int end)
            {
                Key = key;
                Start = start;
                End = end;
            }

            public string Key { get; private set; }
            public int Start { get; private set; }
            public int End { get; private set; }
        }
    }

    internal static class AtomicFile
    {
        public static void WriteAllText(string path, string content, bool createBackup)
        {
            if (path == null)
                throw new ArgumentNullException("path");

            string fullPath = Path.GetFullPath(path);
            string directory = Path.GetDirectoryName(fullPath);
            if (string.IsNullOrEmpty(directory))
                throw new InvalidOperationException("Configuration directory could not be resolved.");
            Directory.CreateDirectory(directory);

            string temporaryPath = Path.Combine(
                directory,
                Path.GetFileName(fullPath) + "." + Guid.NewGuid().ToString("N") + ".tmp");
            var encoding = new UTF8Encoding(false);
            File.WriteAllText(temporaryPath, content, encoding);
            try
            {
                if (File.Exists(fullPath))
                {
                    if (createBackup)
                        File.Copy(fullPath, fullPath + ".gui-backup", true);
                    try
                    {
                        File.Replace(temporaryPath, fullPath, null, true);
                        temporaryPath = null;
                    }
                    catch (PlatformNotSupportedException)
                    {
                        File.Copy(temporaryPath, fullPath, true);
                    }
                    catch (IOException)
                    {
                        File.Copy(temporaryPath, fullPath, true);
                    }
                }
                else
                {
                    File.Move(temporaryPath, fullPath);
                    temporaryPath = null;
                }
            }
            finally
            {
                if (temporaryPath != null && File.Exists(temporaryPath))
                    File.Delete(temporaryPath);
            }
        }
    }

    internal static class EmbeddedDefault
    {
        private const string ResourceName = "MouseMasterConfigurator.DefaultProperties";

        public static string ReadText()
        {
            Assembly assembly = Assembly.GetExecutingAssembly();
            using (Stream stream = assembly.GetManifestResourceStream(ResourceName))
            {
                if (stream == null)
                    throw new InvalidOperationException("Embedded default configuration is missing.");
                using (var reader = new StreamReader(stream, new UTF8Encoding(false), true))
                    return reader.ReadToEnd();
            }
        }
    }
}
