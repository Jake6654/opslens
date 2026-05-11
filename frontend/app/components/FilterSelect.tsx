"use client";

type FilterSelectProps = {
  label: string;
  name: "project" | "environment";
  value?: string;
  options: string[];
  selectedLevel?: string;
  selectedProject?: string;
  selectedEnvironment?: string;
};

export default function FilterSelect({
  label,
  name,
  value,
  options,
  selectedLevel,
  selectedProject,
  selectedEnvironment,
}: FilterSelectProps) {
  function handleChange(nextValue: string) {
    const params = new URLSearchParams();

    if (selectedLevel) params.set("level", selectedLevel);
    if (selectedProject) params.set("project", selectedProject);
    if (selectedEnvironment) params.set("environment", selectedEnvironment);

    if (nextValue) {
      params.set(name, nextValue);
    } else {
      params.delete(name);
    }

    window.location.href = params.toString() ? `/?${params.toString()}` : "/";
  }

  return (
    <div>
      <label className="mb-1 block text-sm font-medium text-gray-700">
        {label}
      </label>

      <select
        value={value ?? ""}
        onChange={(event) => handleChange(event.target.value)}
        className="rounded-md border border-gray-300 bg-white px-3 py-2 text-sm text-gray-700"
      >
        <option value="">All</option>

        {options.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </select>
    </div>
  );
}
